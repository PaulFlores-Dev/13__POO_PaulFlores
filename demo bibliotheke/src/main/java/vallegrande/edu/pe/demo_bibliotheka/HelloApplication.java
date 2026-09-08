package vallegrande.edu.pe.demo_bibliotheka;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));

        // Envolvemos el AnchorPane en un ScrollPane para soportar los 1728px de ancho de Figma
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(fxmlLoader.load());
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);

        Scene scene = new Scene(scrollPane, 1440, 900);
        stage.setTitle("bibliothēke - Sistema de Biblioteca");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}