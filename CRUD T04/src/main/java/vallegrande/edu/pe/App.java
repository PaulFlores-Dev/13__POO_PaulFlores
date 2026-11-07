package vallegrande.edu.pe;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vallegrande.edu.pe.config.AppContext;
import vallegrande.edu.pe.controller.MainController;
import vallegrande.edu.pe.model.UserSession;

import java.io.IOException;

public final class App extends Application {
    private static App instance;
    private AppContext context;
    private Stage stage;

    @Override public void start(Stage primaryStage) throws IOException {
        instance = this;
        context = new AppContext();
        stage = primaryStage;
        stage.setMinWidth(1040);
        stage.setMinHeight(700);
        stage.setTitle("APRAQ · Gestión agraria");
        showLogin();
        stage.show();
    }

    public void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/vallegrande/edu/pe/view/login.fxml"));
        stage.setScene(scene(loader.load(), 1100, 720));
    }

    public void showMain(UserSession session) throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("/vallegrande/edu/pe/view/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.start(session, context);
        stage.setScene(scene(root, 1440, 900));
        stage.centerOnScreen();
    }

    private Scene scene(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(App.class.getResource("/vallegrande/edu/pe/css/app.css").toExternalForm());
        return scene;
    }

    public static App get() { return instance; }
    public AppContext context() { return context; }
    public static void main(String[] args) { launch(args); }
}
