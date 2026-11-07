package vallegrande.edu.pe.view;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vallegrande.edu.pe.model.TableRow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APRAQ_UI_TEST", matches = "true")
class DataTablePaneTest {
    @BeforeAll static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test void rowButtonsDeleteAndRestoreTheClickedRecordWhilePreservingItsData() throws Exception {
        List<TableRow> rows = List.of(row(1, "Agrícola del Valle", "active"),
                row(2, "Productores de Quilmaná", "active"), row(3, "Comercial San Pedro", "inactive"));
        AtomicReference<DataTablePane> holder = new AtomicReference<>();
        AtomicReference<Long> clicked = new AtomicReference<>();
        CountDownLatch loaded = new CountDownLatch(1);
        onFx(() -> {
            DataTablePane pane = new DataTablePane("Empresas clientes", "Use el filtro Estado para restaurar los registros inactivos.", () -> rows);
            holder.set(pane);
            pane.enableStatusActions(item -> {
                clicked.set(item.id("id"));
                item.put("Estado", "active".equals(item.get("Estado")) ? "inactive" : "active");
                pane.reload();
            }, item -> true);
            afterLoad(pane, loaded);
            Scene scene = new Scene(pane, 1180, 600);
            scene.getStylesheets().add(getClass().getResource("/vallegrande/edu/pe/css/app.css").toExternalForm());
        });
        assertTrue(loaded.await(10, TimeUnit.SECONDS));
        DataTablePane pane = holder.get();

        CountDownLatch deleted = new CountDownLatch(1);
        onFx(() -> {
            render(pane);
            assertEquals(List.of(1L, 2L), pane.visibleRows().stream().map(item -> item.id("id")).toList());
            pane.table().getSelectionModel().select(0);
            afterLoad(pane, deleted);
            button(pane, ".delete-row-button", 2).fire();
            assertEquals(2L, clicked.get());
        });
        assertTrue(deleted.await(10, TimeUnit.SECONDS));

        CountDownLatch restored = new CountDownLatch(1);
        onFx(() -> {
            assertEquals(List.of(1L), pane.visibleRows().stream().map(item -> item.id("id")).toList());
            filter(pane).setValue("Inactivos");
            render(pane);
            assertEquals(List.of(2L, 3L), pane.visibleRows().stream().map(item -> item.id("id")).toList());
            pane.table().getSelectionModel().select(1);
            afterLoad(pane, restored);
            button(pane, ".restore-row-button", 2).fire();
            assertEquals(2L, clicked.get());
        });
        assertTrue(restored.await(10, TimeUnit.SECONDS));

        onFx(() -> {
            assertEquals(List.of(3L), pane.visibleRows().stream().map(item -> item.id("id")).toList());
            assertEquals("Productores de Quilmaná", rows.get(1).get("Razón social"));
            assertEquals("active", rows.get(1).get("Estado"));
            filter(pane).setValue("Todos");
            render(pane);
            assertEquals(3, pane.visibleRows().size());
            assertTrue(button(pane, ".restore-row-button", 3).getAccessibleText().contains("Restaurar"));
            savePreview(pane);

            ((TextField) pane.lookup(".search-field")).setText("sin coincidencias");
            render(pane);
            assertTrue(pane.visibleRows().isEmpty());
            assertTrue(pane.lookupAll(".row-action-button").isEmpty(), "Las celdas vacías no deben conservar botones");
        });
    }

    @Test void protectedAccountHasADisabledActionAndReportsKeepAllRows() throws Exception {
        List<TableRow> rows = List.of(row(1, "Mi cuenta", "active"), row(2, "Otra cuenta", "inactive"));
        AtomicReference<DataTablePane> holder = new AtomicReference<>();
        AtomicReference<DataTablePane> report = new AtomicReference<>();
        CountDownLatch loaded = new CountDownLatch(2);
        onFx(() -> {
            DataTablePane pane = new DataTablePane("Usuarios", "Cuentas del personal", () -> rows);
            pane.enableStatusActions(item -> fail("La cuenta protegida no debe modificarse"), item -> item.id("id") != 1);
            holder.set(pane);
            afterLoad(pane, loaded);
            new Scene(pane, 1180, 600);
            DataTablePane plain = new DataTablePane("Reporte", "Solo consulta", () -> rows);
            report.set(plain);
            afterLoad(plain, loaded);
        });
        assertTrue(loaded.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            DataTablePane pane = holder.get();
            render(pane);
            Button ownAccount = button(pane, ".delete-row-button", 1);
            assertTrue(ownAccount.isDisabled());
            assertTrue(ownAccount.getTooltip().getText().contains("propia cuenta"));
            ownAccount.fire();
            assertEquals(2, report.get().visibleRows().size());
            assertNull(report.get().lookup("#status-filter"));
            assertTrue(report.get().table().getColumns().stream().noneMatch(column -> "Acciones".equals(column.getText())));
        });
    }

    private static TableRow row(long id, String name, String status) {
        return new TableRow().put("id", id).put("Razón social", name).put("RUC", "20123456789").put("Estado", status);
    }

    private static void afterLoad(DataTablePane pane, CountDownLatch loaded) {
        pane.disableProperty().addListener((obs, old, busy) -> { if (!busy) Platform.runLater(loaded::countDown); });
    }

    private static void render(DataTablePane pane) {
        pane.resize(1180, 600);
        pane.applyCss();
        pane.table().requestLayout();
        pane.requestLayout();
        pane.layout();
        pane.table().layout();
    }

    private static ComboBox<String> filter(DataTablePane pane) {
        @SuppressWarnings("unchecked") ComboBox<String> filter = (ComboBox<String>) pane.lookup("#status-filter");
        return filter;
    }

    private static Button button(DataTablePane pane, String style, long id) {
        return pane.lookupAll(style).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> Long.valueOf(id).equals(button.getUserData())).findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró " + style + " para #" + id + ": "
                        + pane.lookupAll(".row-action-button").stream().map(node -> ((Button) node).getAccessibleText()).toList()));
    }

    private static void savePreview(DataTablePane pane) throws Exception {
        WritableImage snapshot = pane.snapshot(null, null);
        int width = (int) snapshot.getWidth(), height = (int) snapshot.getHeight();
        int[] pixels = new int[width * height];
        snapshot.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(image, "png", new File("target/ui-status-actions.png"));
    }

    private static void onFx(FxAction action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); } finally { completed.countDown(); }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @FunctionalInterface private interface FxAction { void run() throws Exception; }
}
