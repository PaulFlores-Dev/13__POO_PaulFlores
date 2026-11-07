package vallegrande.edu.pe.concurrent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseQueueTest {
    @Test
    void executesAllDatabaseWorkSequentiallyOnTheSameWorker() {
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Long> first = DatabaseQueue.submit(() -> {
            order.add(1);
            try {
                Thread.sleep(80);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            order.add(2);
            assertEquals("apraq-db-sequential", Thread.currentThread().getName());
            return Thread.currentThread().getId();
        });
        CompletableFuture<Long> second = DatabaseQueue.submit(() -> {
            order.add(3);
            assertEquals("apraq-db-sequential", Thread.currentThread().getName());
            return Thread.currentThread().getId();
        });

        CompletableFuture.allOf(first, second).join();
        assertEquals(List.of(1, 2, 3), order);
        assertEquals(first.join(), second.join());
    }
}
