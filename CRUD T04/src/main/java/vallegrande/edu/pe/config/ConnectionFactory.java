package vallegrande.edu.pe.config;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public final class ConnectionFactory implements ConnectionProvider {
    private final AppConfig config;

    public ConnectionFactory(AppConfig config) {
        this.config = config;
    }

    @Override
    public Connection open() throws SQLException {
        System.out.println("Archivo de configuración: "
                + Path.of("local.properties").toAbsolutePath());

        System.out.println("Conectando a "
                + config.value("DB_HOST", "127.0.0.1") + ":"
                + config.value("DB_PORT", "3307")
                + " / Base: " + config.value("DB_NAME", "apraq")
                + " / SSL: " + config.value("DB_SSL_MODE", "DISABLED"));

        Properties properties = new Properties();
        properties.setProperty("user", config.required("DB_USER"));
        properties.setProperty("password", config.required("DB_PASSWORD"));
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "15000");

        Connection connection = null;

        try {
            connection = DriverManager.getConnection(
                    config.jdbcUrl(), properties);

            System.out.println("Conexión con MySQL establecida.");

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET time_zone = '+00:00'");
            }

            System.out.println("Conexión lista para usar.");
            return connection;

        } catch (SQLException exception) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeException) {
                    exception.addSuppressed(closeException);
                }
            }

            System.err.println("Error MySQL: " + exception.getMessage());
            System.err.println("SQLState: " + exception.getSQLState());
            System.err.println("Código: " + exception.getErrorCode());
            exception.printStackTrace();
            throw exception;
        }
    }

    public void test() throws SQLException {
        try (Connection connection = open()) {
            System.out.println("Prueba de conexión completada.");
        }
    }
}