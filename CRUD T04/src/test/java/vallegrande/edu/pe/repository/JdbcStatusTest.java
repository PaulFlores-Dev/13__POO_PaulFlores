package vallegrande.edu.pe.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vallegrande.edu.pe.exception.BusinessException;
import vallegrande.edu.pe.exception.PersistenceException;
import vallegrande.edu.pe.model.DbUser;
import vallegrande.edu.pe.model.StatusEntity;
import vallegrande.edu.pe.model.UserSession;
import vallegrande.edu.pe.repository.jdbc.JdbcApraqRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcStatusTest {
    private Connection connection;
    private PreparedStatement statement;
    private JdbcApraqRepository repository;
    private final UserSession admin = session(1, "admin");

    @BeforeEach void setUp() throws Exception {
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        repository = new JdbcApraqRepository(() -> connection);
    }

    @ParameterizedTest @EnumSource(StatusEntity.class)
    void deleteOnlyChangesTheStatusOfTheClickedRecord(StatusEntity entity) throws Exception {
        repository.setActive(entity, 42, false, admin);

        verify(connection).prepareStatement("UPDATE " + entity.table() + " SET status=? WHERE " + entity.idColumn() + "=? AND status=?");
        verify(statement).setString(1, "inactive");
        verify(statement).setLong(2, 42);
        verify(statement).setString(3, "active");
        verify(statement).executeUpdate();
        verify(statement).close();
        verify(connection).close();
    }

    @ParameterizedTest @EnumSource(StatusEntity.class)
    void restoreReactivatesTheSameRecordWithoutReinsertingIt(StatusEntity entity) throws Exception {
        repository.setActive(entity, 42, true, admin);

        verify(statement).setString(1, "active");
        verify(statement).setLong(2, 42);
        verify(statement).setString(3, "inactive");
        verify(statement, times(1)).executeUpdate();
    }

    @Test void staleOrMissingRecordDoesNotReportASuccess() throws Exception {
        when(statement.executeUpdate()).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class,
                () -> repository.setActive(StatusEntity.MEMBERS, 42, false, admin));
        assertTrue(error.getMessage().contains("Actualice la tabla"));
    }

    @Test void sqlFailureIsReportedWithoutPretendingTheRecordWasDeleted() throws Exception {
        when(statement.executeUpdate()).thenThrow(new SQLException("connection lost"));
        assertThrows(PersistenceException.class,
                () -> repository.setActive(StatusEntity.COMPANIES, 42, false, admin));
    }

    @Test void userCannotDeleteTheirOwnAccessAccount() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> repository.setActive(StatusEntity.USERS, 1, false, admin));
        assertTrue(error.getMessage().contains("propia cuenta"));
        verifyNoInteractions(connection);
    }

    @ParameterizedTest @EnumSource(StatusEntity.class)
    void editorialRoleCannotChangeOperationalOrUserStatuses(StatusEntity entity) {
        assertThrows(BusinessException.class,
                () -> repository.setActive(entity, 42, false, session(2, "editor")));
        verifyNoInteractions(connection);
    }

    @Test void eachOperationalRoleCanManageItsOwnModule() {
        assertDoesNotThrow(() -> repository.setActive(StatusEntity.COMPANIES, 42, false, session(2, "sales")));
        assertDoesNotThrow(() -> repository.setActive(StatusEntity.MEMBERS, 42, false, session(3, "warehouse")));
        assertThrows(BusinessException.class,
                () -> repository.setActive(StatusEntity.USERS, 42, false, session(2, "sales")));
        assertThrows(BusinessException.class,
                () -> repository.setActive(StatusEntity.CATEGORIES, 42, false, session(3, "warehouse")));
    }

    @Test void invalidTargetsAndIdsAreRejectedBeforeOpeningTheDatabase() {
        assertThrows(IllegalArgumentException.class, () -> repository.setActive(null, 42, false, admin));
        assertThrows(IllegalArgumentException.class, () -> repository.setActive(StatusEntity.MEMBERS, 0, true, admin));
        assertThrows(BusinessException.class, () -> repository.setActive(StatusEntity.MEMBERS, 42, true, null));
        verifyNoInteractions(connection);
    }

    @Test void newProductSelectionsExcludeInactiveProductsAndCategories() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(rows);
        repository.lookup("products");

        verify(connection).prepareStatement(contains("p.status='active' AND c.status='active'"));
    }

    private static UserSession session(long id, String role) {
        return new UserSession(new DbUser(id, "user" + id, "Usuario de prueba", role, "active"));
    }
}
