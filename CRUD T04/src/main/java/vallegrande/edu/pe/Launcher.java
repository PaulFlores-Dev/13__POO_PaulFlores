package vallegrande.edu.pe;

import javafx.application.Application;

/**
 * Punto de entrada independiente de JavaFX para las distribuciones ejecutables.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
