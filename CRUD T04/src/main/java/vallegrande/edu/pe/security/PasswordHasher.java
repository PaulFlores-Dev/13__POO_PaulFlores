package vallegrande.edu.pe.security;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {
    private static final int WORK_FACTOR = 12;

    private PasswordHasher() {
    }

    public static String hash(String password) {
        validate(password);
        return BCrypt.hashpw(password, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean matches(String password, String hash) {
        if (password == null || password.isBlank() || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres");
        }
        if (password.length() > 72) {
            throw new IllegalArgumentException("La contraseña no puede superar 72 caracteres");
        }
    }
}
