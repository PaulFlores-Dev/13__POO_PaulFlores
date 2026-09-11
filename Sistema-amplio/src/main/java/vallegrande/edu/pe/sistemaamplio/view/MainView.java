package vallegrande.edu.pe.sistemaamplio.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class MainView extends BorderPane {
    private Button btnInicio;
    private Button btnUsuarios;
    private Button btnProductos;
    private Button btnReportes;
    private Button btnConfiguracion;
    private Button btnCitas;

    public MainView(){
        crearMenu();
        mostrarInicio();
    }

    private void crearMenu(){
        VBox menu = new VBox(15);
        menu.setPadding(new Insets(25));
        menu.setPrefWidth(220);

        Label titulo = new Label("🖥️ MI SISTEMA");
        titulo.setStyle(
                "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;"
        );

        btnInicio = crearBoton("Inicio");
        btnUsuarios = crearBoton("Usuarios");
        btnProductos = crearBoton("Productos");
        btnReportes = crearBoton("Reportes");
        btnConfiguracion = crearBoton("Configuración");
        btnCitas = crearBoton("Citas");

        menu.getChildren().addAll(
                titulo,
                btnInicio,
                btnUsuarios,
                btnProductos,
                btnReportes,
                btnConfiguracion,
                btnCitas
        );

        menu.setStyle("-fx-background-color: #2563EB;");
        setLeft(menu);
    }

    private Button crearBoton(String texto){
        Button boton = new Button(texto);
        boton.setPrefWidth(170);
        boton.setPrefHeight(40);
        boton.setStyle("-fx-background-color: white;" +
                "-fx-text-fill: #1E3A8A;" +
                "-fx-font-size: 14px;" +
                "-fx-background-radius: 8;"
        );
        return boton;
    }

    public void mostrarInicio(){
        VBox contenido = new VBox(10);
        contenido.setAlignment(Pos.CENTER);

        Label titulo = new Label("BIENVENIDO");
        titulo.setStyle("-fx-font-size: 28px;" +
                "-fx-font-weight: bold;");

        Label texto = new Label("Panel principal de mi sistema");

        contenido.getChildren().addAll(
                titulo,
                texto
        );
        setCenter(contenido);
    }

    public void mostrarUsuarios(){
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));

        Label titulo = new Label("GESTIÓN DE USUARIOS");
        titulo.setStyle("-fx-font-size: 26px;" +
                "-fx-font-weight: bold;");

        HBox tarjetas = new HBox(15);
        tarjetas.getChildren().addAll(
                crearTarjeta("Carlos Perez", "Administrador"),
                crearTarjeta("Maria Lopez", "Vendedora"),
                crearTarjeta("Piero Ramos", "Supervisor")
        );

        contenido.getChildren().addAll(
                titulo,
                tarjetas
        );
        setCenter(contenido);
    }

    public void mostrarProductos(){
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));

        Label titulo = new Label("PRODUCTOS");
        titulo.setStyle("-fx-font-size: 26px;" +
                "-fx-font-weight: bold;");

        HBox tarjetas = new HBox(15);
        tarjetas.getChildren().addAll(
                crearTarjeta("Laptop Lenovo", "S/ 2500"),
                crearTarjeta("Mouse Logitech", "S/ 80"),
                crearTarjeta("Teclado Mecánico", "S/ 180")
        );

        contenido.getChildren().addAll(
                titulo,
                tarjetas
        );
        setCenter(contenido);
    }

    public void mostrarReportes(){
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));

        Label titulo = new Label("REPORTES");
        titulo.setStyle("-fx-font-size: 26px;" +
                "-fx-font-weight: bold;");

        HBox tarjetas = new HBox(15);
        tarjetas.getChildren().addAll(
                crearTarjeta("Reporte de Ventas", "120 Ventas hoy"),
                crearTarjeta("Productos Registrados", "45 en Total"),
                crearTarjeta("Usuarios Activos", "8 Conectados")
        );

        contenido.getChildren().addAll(
                titulo,
                tarjetas
        );
        setCenter(contenido);
    }

    public void mostrarConfiguracion(){
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));

        Label titulo = new Label("CONFIGURACIÓN");
        titulo.setStyle("-fx-font-size: 26px;" +
                "-fx-font-weight: bold;");

        HBox tarjetas = new HBox(15);
        tarjetas.getChildren().addAll(
                crearTarjeta("Tema Visual", "Modo Claro"),
                crearTarjeta("Notificaciones", "Activadas"),
                crearTarjeta("Base de Datos", "Conectado")
        );

        contenido.getChildren().addAll(
                titulo,
                tarjetas
        );
        setCenter(contenido);
    }

    public void mostrarCitas(){
        VBox contenido = new VBox(20);
        contenido.setPadding(new Insets(30));

        Label titulo = new Label("CITAS");
        titulo.setStyle("-fx-font-size: 26px;" +
                "-fx-font-weight: bold;");

        HBox tarjetas = new HBox(15);
        tarjetas.getChildren().addAll(
                crearTarjeta("Cita #101", "10:00 AM - Juan"),
                crearTarjeta("Cita #102", "11:30 AM - Ana"),
                crearTarjeta("Cita #103", "03:00 PM - Luis")
        );

        contenido.getChildren().addAll(
                titulo,
                tarjetas
        );
        setCenter(contenido);
    }

    private VBox crearTarjeta(String titulo, String detalle){
        VBox tarjeta = new VBox(8);
        tarjeta.setPadding(new Insets(20));
        tarjeta.setPrefWidth(180);
        tarjeta.setStyle("-fx-background-color: #EAF2FF;" +
                "-fx-background-radius: 12;");

        Label nombre = new Label(titulo);
        nombre.setStyle("-fx-font-size: 16px;" + "-fx-font-weight: bold;");

        Label info = new Label(detalle);

        tarjeta.getChildren().addAll(
                nombre,
                info
        );
        return tarjeta;
    }

    public Button getBtnInicio(){
        return btnInicio;
    }

    public Button getBtnUsuarios(){
        return btnUsuarios;
    }

    public Button getBtnProductos(){
        return btnProductos;
    }

    public Button getBtnReportes(){
        return btnReportes;
    }

    public Button getBtnConfiguracion(){
        return btnConfiguracion;
    }

    public Button getBtnCitas(){
        return btnCitas;
    }
}