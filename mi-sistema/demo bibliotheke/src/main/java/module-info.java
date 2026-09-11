module vallegrande.edu.pe.demo_bibliotheka {
    requires javafx.controls;
    requires javafx.fxml;

    opens vallegrande.edu.pe.demo_bibliotheka to javafx.fxml;
    opens vallegrande.edu.pe.demo_bibliotheka.controller to javafx.fxml;

    exports vallegrande.edu.pe.demo_bibliotheka;
    exports vallegrande.edu.pe.demo_bibliotheka.controller;
    exports vallegrande.edu.pe.demo_bibliotheka.model;
}