package vallegrande.edu.pe.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class AppConfig {
    private final Properties local = new Properties();

    private AppConfig() {
        Path path = Path.of("local.properties");
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                local.load(input);
            } catch (IOException exception) {
                throw new IllegalStateException("No se pudo leer local.properties", exception);
            }
        }
    }

    public static AppConfig load() {
        return new AppConfig();
    }

    public String value(String key, String fallback) {
        return Optional.ofNullable(System.getenv(key))
                .filter(value -> !value.isBlank())
                .or(() -> Optional.ofNullable(local.getProperty(key)).filter(value -> !value.isBlank()))
                .orElse(fallback);
    }

    public String required(String key) {
        String value = value(key, "");
        if (value.isBlank()) {
            throw new IllegalStateException("Falta configurar " + key + " en el entorno o local.properties");
        }
        return value;
    }

    public String jdbcUrl() {
        String host = value("DB_HOST", "127.0.0.1");
        String port = value("DB_PORT", "3307");
        String database = value("DB_NAME", "apraq");
        String sslMode = value("DB_SSL_MODE", "DISABLED").toUpperCase(Locale.ROOT);
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(port).append('/').append(database)
                .append("?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true")
                .append("&sslMode=").append(sslMode)
                .append("&allowPublicKeyRetrieval=true");
        appendJdbc(url,"trustCertificateKeyStoreUrl",value("DB_TRUST_STORE_URL", ""));
        appendJdbc(url,"trustCertificateKeyStoreType",value("DB_TRUST_STORE_TYPE", ""));
        appendJdbc(url,"trustCertificateKeyStorePassword",value("DB_TRUST_STORE_PASSWORD", ""));
        appendJdbc(url,"fallbackToSystemTrustStore",value("DB_FALLBACK_TO_SYSTEM_TRUST_STORE", ""));
        return url.toString();
    }

    private static void appendJdbc(StringBuilder url,String key,String value){
        if(!value.isBlank())url.append('&').append(key).append('=').append(URLEncoder.encode(value,StandardCharsets.UTF_8));
    }

}
