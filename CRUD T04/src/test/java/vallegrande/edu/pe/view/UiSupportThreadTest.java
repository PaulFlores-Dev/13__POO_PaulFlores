package vallegrande.edu.pe.view;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APRAQ_INTEGRATION_TEST", matches = "true")
class UiSupportThreadTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                started.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(started::countDown);
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void databaseWorkRunsInBackgroundAndSuccessAlwaysReturnsToJavaFx() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean workWasInBackground = new AtomicBoolean();
        AtomicBoolean successWasOnJavaFx = new AtomicBoolean();

        Platform.runLater(() -> UiSupport.async(new Pane(), () -> {
            workWasInBackground.set(!Platform.isFxApplicationThread());
            return "listo";
        }, value -> {
            successWasOnJavaFx.set(Platform.isFxApplicationThread());
            assertEquals("listo", value);
            completed.countDown();
        }));

        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertTrue(workWasInBackground.get());
        assertTrue(successWasOnJavaFx.get());
    }

    @Test
    void validationIsRenderedInsideTheForm() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean rendered = new AtomicBoolean();

        Platform.runLater(() -> {
            Label message = new Label();
            message.setManaged(false);
            message.setVisible(false);

            UiSupport.showValidation(message, new IllegalArgumentException("Seleccione una campaña"));
            rendered.set(message.isManaged()
                    && message.isVisible()
                    && message.getText().equals("Revise los datos: Seleccione una campaña"));
            completed.countDown();
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertTrue(rendered.get());
    }

    @Test
    void invalidSaveKeepsTheDialogOpenAndShowsTheMessage() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean stayedOpenWithMessage = new AtomicBoolean();

        Platform.runLater(() -> {
            Platform.runLater(() -> {
                Window window = Window.getWindows().stream()
                        .filter(Window::isShowing)
                        .filter(item -> item.getScene() != null
                                && item.getScene().getRoot() instanceof DialogPane)
                        .findFirst()
                        .orElseThrow();
                DialogPane pane = (DialogPane) window.getScene().getRoot();
                Button save = (Button) pane.lookupButton(pane.getButtonTypes().stream()
                        .filter(type -> type.getText().equals("Guardar"))
                        .findFirst()
                        .orElseThrow());

                save.fire();
                Node message = pane.lookup(".validation-message");
                stayedOpenWithMessage.set(window.isShowing()
                        && message != null
                        && message.isVisible()
                        && ((Label) message).getText().contains("Seleccione una campaña"));
                window.hide();
            });

            GridPane form = UiSupport.form();
            UiSupport.dialog("Validación", form, () -> {
                throw new IllegalArgumentException("Seleccione una campaña");
            });
            completed.countDown();
        });

        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertTrue(stayedOpenWithMessage.get());
    }
}
