package vallegrande.edu.pe.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vallegrande.edu.pe.App;
import vallegrande.edu.pe.config.AppContext;
import vallegrande.edu.pe.model.UserSession;
import vallegrande.edu.pe.view.ModuleFactory;

import java.io.IOException;

public final class MainController {
    @FXML private StackPane content;
    @FXML private Label userLabel;
    @FXML private Label dbUserLabel;
    @FXML private VBox navigationList;
    private ModuleFactory modules;
    private UserSession session;

    public void start(UserSession session, AppContext context) {
        this.session = session;
        userLabel.setText(session.user().fullName());
        dbUserLabel.setText("@" + session.username() + " · " + session.roleLabel());
        modules = new ModuleFactory(context, session);
        applyPermissions();
        open("Inicio");
        navigationButtons().filter(button -> "Inicio".equals(button.getUserData()))
                .findFirst().ifPresent(button -> button.getStyleClass().add("nav-active"));
    }

    @FXML
    private void navigate(ActionEvent event) {
        Button source = (Button) event.getSource();
        String module = String.valueOf(source.getUserData());
        if (!session.canAccess(module)) return;
        open(module);
        navigationButtons().forEach(button -> button.getStyleClass().remove("nav-active"));
        source.getStyleClass().add("nav-active");
    }

    private void applyPermissions() {
        navigationButtons().forEach(button -> {
            boolean allowed = session.canAccess(String.valueOf(button.getUserData()));
            button.setVisible(allowed);
            button.setManaged(allowed);
        });
    }

    private java.util.stream.Stream<Button> navigationButtons() {
        return navigationList.getChildren().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast);
    }

    private void open(String name) {
        if (!session.canAccess(name)) return;
        Node node = switch (name) {
            case "Inicio" -> modules.dashboard();
            case "Catálogos" -> modules.catalogs();
            case "Productores" -> modules.producers();
            case "Clientes" -> modules.clients();
            case "Solicitudes web" -> modules.requests();
            case "Cotizaciones" -> modules.quotations();
            case "Ventas" -> modules.sales();
            case "Acopio" -> modules.collection();
            case "Inventario" -> modules.inventory();
            case "Despachos" -> modules.dispatches();
            case "Noticias" -> modules.news();
            case "Usuarios" -> modules.users();
            case "Reportes" -> modules.reports();
            default -> modules.dashboard();
        };
        content.getChildren().setAll(node);
    }

    @FXML
    private void logout() {
        try {
            App.get().showLogin();
        } catch (IOException ignored) {
        }
    }
}
