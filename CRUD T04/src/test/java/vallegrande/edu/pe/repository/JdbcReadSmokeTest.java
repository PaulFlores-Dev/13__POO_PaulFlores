package vallegrande.edu.pe.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vallegrande.edu.pe.config.AppConfig;
import vallegrande.edu.pe.config.ConnectionFactory;
import vallegrande.edu.pe.repository.jdbc.JdbcApraqRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "APRAQ_INTEGRATION_TEST", matches = "true")
class JdbcReadSmokeTest {
    @Test void everyDesktopQueryRunsAgainstTheIndependentDockerDatabase() {
        ConnectionFactory connections = new ConnectionFactory(AppConfig.load());
        ApraqRepository repository = new JdbcApraqRepository(connections);
        assertDoesNotThrow(repository::ensureInitialUsers);
        assertEquals("admin", repository.authenticate("admin", "admin123").orElseThrow().role());
        assertTrue(repository.authenticate("admin", "contraseña-incorrecta").isEmpty());
        assertDoesNotThrow(repository::dashboard);
        assertDoesNotThrow(repository::users);
        for (String lookup : List.of("categories","products","members","campaigns","companies","people",
                "contacts","locations","lots","sales","requests","accepted_quotations")) {
            assertDoesNotThrow(() -> repository.lookup(lookup), lookup);
        }
        for (String view : List.of("categories","products","locations","members","campaigns","companies",
                "people","contacts","requests","quotations","sales","lots","inventory","movements",
                "reservations","dispatches","news","users")) {
            assertDoesNotThrow(() -> repository.rows(view), view);
        }
    }

    @Test void administratorWorkflowCanCreateAndUpdateAnAuthenticatableRoleAccount() throws Exception {
        ConnectionFactory connections = new ConnectionFactory(AppConfig.load());
        ApraqRepository repository = new JdbcApraqRepository(connections);
        repository.ensureInitialUsers();
        long contactId = repository.lookup("contacts").get(0).id();
        String username = "it_" + UUID.randomUUID().toString().substring(0, 12);
        try {
            repository.saveUser(null, username, "claveSegura123", contactId, "sales", "active");
            var created = repository.authenticate(username, "claveSegura123").orElseThrow();
            assertEquals("sales", created.role());

            repository.saveUser(created.id(), username, "", contactId, "editor", "active");
            assertEquals("editor", repository.authenticate(username, "claveSegura123").orElseThrow().role());
        } finally {
            try (Connection connection = connections.open();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE username=?")) {
                statement.setString(1, username);
                statement.executeUpdate();
            }
        }
    }
}
