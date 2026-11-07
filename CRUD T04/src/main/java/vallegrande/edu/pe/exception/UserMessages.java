package vallegrande.edu.pe.exception;

import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Convierte fallos internos en mensajes seguros y comprensibles para la interfaz. */
public final class UserMessages {
    private static final String UNEXPECTED = "Ocurrió un error inesperado. Intente nuevamente.";

    private UserMessages() {
    }

    public static String from(Throwable error) {
        if (error == null) return UNEXPECTED;

        Throwable current = unwrapAsync(error);
        for (Throwable cause = current; cause != null; cause = cause.getCause()) {
            if (cause instanceof BusinessException
                    || cause instanceof PersistenceException
                    || cause instanceof IllegalArgumentException) {
                String message = clean(cause.getMessage());
                if (message != null) return message;
            }
        }

        for (Throwable cause = current; cause != null; cause = cause.getCause()) {
            String mapped = mapTechnical(cause);
            if (mapped != null) return mapped;
        }
        return UNEXPECTED;
    }

    private static Throwable unwrapAsync(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String mapTechnical(Throwable error) {
        String message = clean(error.getMessage());
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);

        if (error instanceof SQLException sql && sql.getSQLState() != null
                && sql.getSQLState().startsWith("23")) {
            return "Los datos ya existen o están relacionados con otro registro. Revise la información ingresada.";
        }
        if (normalized.contains("duplicate entry") || normalized.contains("unique constraint")) {
            return "Ya existe un registro con esos datos.";
        }
        if (normalized.contains("cannot add or update a child row")
                || normalized.contains("foreign key constraint")) {
            return "Uno de los registros seleccionados ya no está disponible. Actualice la información e intente nuevamente.";
        }
        if (normalized.contains("data truncation") || normalized.contains("data too long")) {
            return "Uno de los valores supera el tamaño permitido.";
        }
        if (normalized.contains("communications link failure")
                || normalized.contains("connection refused")
                || normalized.contains("could not create connection")) {
            return "No se pudo conectar con MySQL. Verifique que la base de datos esté activa.";
        }
        return null;
    }

    private static String clean(String message) {
        if (message == null || message.isBlank()) return null;
        return message.trim();
    }
}
