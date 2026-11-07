package vallegrande.edu.pe.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Cola única para todas las llamadas a MySQL. Evita operaciones concurrentes
 * desde la interfaz y conserva el orden exacto en que el usuario las solicita.
 */
public final class DatabaseQueue {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "apraq-db-sequential");
        thread.setDaemon(true);
        return thread;
    });

    private DatabaseQueue() {
    }

    public static <T> CompletableFuture<T> submit(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, EXECUTOR);
    }
}
