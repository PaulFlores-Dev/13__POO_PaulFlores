package vallegrande.edu.pe.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test
    void hashesAndVerifiesPasswordsWithoutStoringPlainText() {
        String hash = PasswordHasher.hash("segura123");

        assertTrue(hash.startsWith("$2a$"));
        assertNotEquals("segura123", hash);
        assertTrue(PasswordHasher.matches("segura123", hash));
        assertFalse(PasswordHasher.matches("incorrecta", hash));
    }

    @Test
    void rejectsShortPasswordsAndMalformedHashes() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash("corta"));
        assertFalse(PasswordHasher.matches("segura123", "hash-invalido"));
    }
}
