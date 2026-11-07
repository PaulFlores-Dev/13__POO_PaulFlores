package vallegrande.edu.pe.exception;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserMessagesTest {
    @Test
    void keepsBusinessValidationInsteadOfTheTechnicalCause() {
        Throwable failure = new CompletionException(
                new PersistenceException("No se pudo guardar la información. Los datos duplican un registro.",
                        new SQLException("Duplicate entry '123' for key 'members.uk_dni'", "23000")));

        assertEquals("No se pudo guardar la información. Los datos duplican un registro.",
                UserMessages.from(failure));
    }

    @Test
    void mapsRawConstraintErrorsToAUserFacingMessage() {
        SQLException failure = new SQLException("Duplicate entry '123' for key 'users.uk_username'", "23000");

        assertEquals("Los datos ya existen o están relacionados con otro registro. Revise la información ingresada.",
                UserMessages.from(failure));
    }

    @Test
    void doesNotExposeUnknownTechnicalDetails() {
        assertEquals("Ocurrió un error inesperado. Intente nuevamente.",
                UserMessages.from(new NullPointerException("internal_field")));
    }
}
