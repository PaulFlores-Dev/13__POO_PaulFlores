package vallegrande.edu.pe.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import vallegrande.edu.pe.App;
import vallegrande.edu.pe.concurrent.DatabaseQueue;
import vallegrande.edu.pe.exception.UserMessages;
import vallegrande.edu.pe.model.UserSession;

import java.io.IOException;

public final class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label connectionStatus;

    @FXML
    public void initialize() {
        usernameField.setText("admin");
        passwordField.setText("admin123");
        prepareAccounts();
    }

    private void prepareAccounts() {
        loginButton.setDisable(true);
        connectionStatus.setText("Preparando la conexión segura con MySQL…");
        DatabaseQueue.submit(() -> {
            App.get().context().repository().ensureInitialUsers();
            return null;
        }).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            if (failure == null) {
                connectionStatus.setText("MySQL conectado · autenticación y perfiles disponibles");
                loginButton.setDisable(false);
                usernameField.requestFocus();
            } else {
                connectionStatus.setText("Sin conexión: " + rootMessage(failure));
                loginButton.setDisable(true);
            }
        }));
    }

    @FXML
    private void login() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isBlank() || password.isBlank()) {
            error("Complete sus datos", "Ingrese el usuario y la contraseña.");
            return;
        }

        loginButton.setDisable(true);
        connectionStatus.setText("Validando credenciales…");
        DatabaseQueue.submit(() -> App.get().context().repository().authenticate(username, password))
                .whenComplete((authenticated, failure) -> Platform.runLater(() -> {
            loginButton.setDisable(false);
            if (failure != null) {
                connectionStatus.setText("No se pudo validar el acceso");
                error("Error de conexión", rootMessage(failure));
                return;
            }
            if (authenticated.isEmpty()) {
                connectionStatus.setText("Credenciales no válidas o cuenta inactiva");
                error("Acceso no válido", "Verifique el usuario, la contraseña y el estado de la cuenta.");
                passwordField.selectAll();
                passwordField.requestFocus();
                return;
            }
            try {
                App.get().showMain(new UserSession(authenticated.get()));
            } catch (IOException exception) {
                error("No se pudo abrir la aplicación", exception.getMessage());
            }
        }));
    }

    @FXML private void retryConnection() {
        prepareAccounts();
    }

    private static void error(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.getDialogPane().getStylesheets().add(
                LoginController.class.getResource("/vallegrande/edu/pe/css/app.css").toExternalForm());
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private static String rootMessage(Throwable error) {
        return UserMessages.from(error);
    }
}
