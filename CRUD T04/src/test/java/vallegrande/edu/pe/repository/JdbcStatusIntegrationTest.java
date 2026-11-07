package vallegrande.edu.pe.repository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vallegrande.edu.pe.model.DbUser;
import vallegrande.edu.pe.model.StatusEntity;
import vallegrande.edu.pe.model.TableRow;
import vallegrande.edu.pe.model.UserSession;
import vallegrande.edu.pe.repository.jdbc.JdbcApraqRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Se ejecuta únicamente contra una base temporal explícita, nunca contra local.properties. */
@EnabledIfEnvironmentVariable(named = "APRAQ_STATUS_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/apraq_status_test.*")
class JdbcStatusIntegrationTest {
    private static JdbcApraqRepository repository;
    private static UserSession admin;

    @BeforeAll static void prepare() {
        repository = new JdbcApraqRepository(JdbcStatusIntegrationTest::connect);
        repository.ensureInitialUsers();
        admin = new UserSession(repository.authenticate("admin", "admin123").orElseThrow());
    }

    @ParameterizedTest @EnumSource(StatusEntity.class)
    void completeCrudPreservesTheRecordAndRestoresAvailability(StatusEntity entity) throws Exception {
        String key = "crud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Fixture fixture = create(entity, key);
        assertEquals("active", row(entity, fixture.id()).get("Estado"));

        update(entity, fixture, key);
        Map<String, Object> beforeDelete = row(entity, fixture.id()).values();
        assertEquals(key + "_updated", beforeDelete.get(fixture.nameKey()));

        repository.setActive(entity, fixture.id(), false, admin);
        TableRow deleted = row(entity, fixture.id());
        assertEquals("inactive", deleted.get("Estado"));
        beforeDelete.forEach((field, value) -> {
            if (!"Estado".equals(field)) assertEquals(value, deleted.get(field), "La eliminación debe conservar " + field);
        });
        if (entity == StatusEntity.USERS) {
            assertTrue(repository.authenticate(key + "_updated", "ClavePrueba123!").isEmpty());
        } else {
            assertTrue(repository.lookup(entity.table()).stream().noneMatch(item -> item.id() == fixture.id()));
        }

        repository.setActive(entity, fixture.id(), true, admin);
        assertEquals(beforeDelete, row(entity, fixture.id()).values());
        if (entity == StatusEntity.USERS) {
            assertEquals(fixture.id(), repository.authenticate(key + "_updated", "ClavePrueba123!").orElseThrow().id());
        } else {
            assertTrue(repository.lookup(entity.table()).stream().anyMatch(item -> item.id() == fixture.id()));
        }
        if (entity == StatusEntity.PRODUCTS) {
            repository.setActive(StatusEntity.CATEGORIES, fixture.relatedId(), false, admin);
            assertTrue(repository.lookup("products").stream().noneMatch(item -> item.id() == fixture.id()));
            assertEquals("active", row(entity, fixture.id()).get("Estado"));
            repository.setActive(StatusEntity.CATEGORIES, fixture.relatedId(), true, admin);
            assertTrue(repository.lookup("products").stream().anyMatch(item -> item.id() == fixture.id()));
        }
    }

    private static Fixture create(StatusEntity entity, String key) throws Exception {
        return switch (entity) {
            case CATEGORIES -> {
                repository.saveCategory(null, key, "active");
                yield new Fixture(findId(entity, "name", key), "Nombre", 0, null);
            }
            case PRODUCTS -> {
                repository.saveCategory(null, key + "_category", "active");
                long categoryId = findId(StatusEntity.CATEGORIES, "name", key + "_category");
                repository.saveProduct(null, key, categoryId, "active");
                yield new Fixture(findId(entity, "name", key), "Producto", categoryId, null);
            }
            case MEMBERS -> {
                String dni = String.format("%08d", Math.floorMod(key.hashCode(), 100_000_000));
                repository.saveMember(null, dni, key, "Prueba", "999123456", LocalDate.of(2026, 10, 20), "Dirección de prueba", "active");
                yield new Fixture(findId(entity, "national_id_number", dni), "Nombres", 0, dni);
            }
            case COMPANIES -> {
                String ruc = "20" + String.format("%09d", Math.floorMod(key.hashCode(), 1_000_000_000));
                repository.saveCompany(null, key, ruc, "active");
                yield new Fixture(findId(entity, "ruc", ruc), "Razón social", 0, ruc);
            }
            case USERS -> {
                repository.savePerson(null, key, "Prueba", null, null);
                long personId = find("SELECT person_id FROM people WHERE first_name=?", key);
                repository.saveContact(null, personId, null, null, key + "@example.com", null, null);
                long contactId = find("SELECT contact_id FROM contacts WHERE email=?", key + "@example.com");
                repository.saveUser(null, key, "ClavePrueba123!", contactId, "sales", "active");
                yield new Fixture(findId(entity, "username", key), "Usuario", contactId, null);
            }
        };
    }

    private static void update(StatusEntity entity, Fixture fixture, String key) {
        switch (entity) {
            case CATEGORIES -> repository.saveCategory(fixture.id(), key + "_updated", "active");
            case PRODUCTS -> repository.saveProduct(fixture.id(), key + "_updated", fixture.relatedId(), "active");
            case MEMBERS -> repository.saveMember(fixture.id(), fixture.document(), key + "_updated", "Prueba", "999123456", LocalDate.of(2026, 10, 20), "Dirección de prueba", "active");
            case COMPANIES -> repository.saveCompany(fixture.id(), key + "_updated", fixture.document(), "active");
            case USERS -> repository.saveUser(fixture.id(), key + "_updated", "", fixture.relatedId(), "sales", "active");
        }
    }

    private static TableRow row(StatusEntity entity, long id) {
        return repository.rows(entity.table()).stream().filter(item -> item.id("id") == id).findFirst().orElseThrow();
    }

    private static long findId(StatusEntity entity, String field, String value) throws Exception {
        return find("SELECT " + entity.idColumn() + " FROM " + entity.table() + " WHERE " + field + "=?", value);
    }

    private static long find(String sql, String value) throws Exception {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private static Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(System.getenv("APRAQ_STATUS_JDBC_URL"),
                System.getenv("APRAQ_STATUS_DB_USER"), System.getenv("APRAQ_STATUS_DB_PASSWORD"));
    }

    private record Fixture(long id, String nameKey, long relatedId, String document) {}
}
