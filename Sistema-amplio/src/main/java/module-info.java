module vallegrande.edu.pe.sistemaamplio {
    requires javafx.controls;
    requires javafx.fxml;

    opens vallegrande.edu.pe.sistemaamplio.view to javafx.fxml;
    opens vallegrande.edu.pe.sistemaamplio.controller to javafx.fxml;

    exports vallegrande.edu.pe.sistemaamplio.view;
    exports vallegrande.edu.pe.sistemaamplio.controller;
}