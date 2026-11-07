package vallegrande.edu.pe.controller;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vallegrande.edu.pe.App;
import vallegrande.edu.pe.config.AppConfig;
import vallegrande.edu.pe.config.AppContext;
import vallegrande.edu.pe.config.ConnectionFactory;
import vallegrande.edu.pe.model.UserSession;
import vallegrande.edu.pe.repository.ApraqRepository;
import vallegrande.edu.pe.repository.jdbc.JdbcApraqRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APRAQ_INTEGRATION_TEST", matches = "true")
class JavaFxViewSmokeTest {
    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void loginAndAdministrativeUserModuleLoadWithTheDesignSystem() throws Exception {
        AppContext context = new AppContext();
        ApraqRepository repository = new JdbcApraqRepository(new ConnectionFactory(AppConfig.load()));
        repository.ensureInitialUsers();
        UserSession admin = new UserSession(repository.authenticate("admin", "admin123").orElseThrow());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch rendered = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Parent login = FXMLLoader.load(App.class.getResource("/vallegrande/edu/pe/view/login.fxml"));
                assertNotNull(login);

                FXMLLoader loader = new FXMLLoader(App.class.getResource("/vallegrande/edu/pe/view/main.fxml"));
                Parent main = loader.load();
                loader.<MainController>getController().start(admin, context);
                Scene scene = new Scene(main, 1440, 900);
                scene.getStylesheets().add(App.class.getResource("/vallegrande/edu/pe/css/app.css").toExternalForm());
                main.applyCss();

                Button users = main.lookupAll(".button").stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(button -> "Usuarios".equals(button.getUserData()))
                        .findFirst().orElseThrow();
                assertTrue(users.isVisible());
                users.fire();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                rendered.countDown();
            }
        });

        assertTrue(rendered.await(15, TimeUnit.SECONDS));
        if (failure.get() != null) fail(failure.get());
    }
}
