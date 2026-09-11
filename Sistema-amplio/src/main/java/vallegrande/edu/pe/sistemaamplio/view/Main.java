package vallegrande.edu.pe.sistemaamplio.view;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vallegrande.edu.pe.sistemaamplio.controller.MainController;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        MainView view = new MainView();
        new MainController(view);

        Scene scene = new Scene(view, 900, 600);
        stage.setTitle("Sistema Amplio");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}