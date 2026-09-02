package vallegrande.edu.pe.miperfil.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class PerfilView {
    private VBox contenedor;
    private Label titulo;
    private TextField txtNombre;
    private TextField txtCarrera;
    private TextField txtSemestre;
    private TextField txtVideojuego;
    private Button btnMostrar;
    private Button btnLimpiar;
    private Label lblResultado;

    public PerfilView(){
        titulo = new Label("MI PERFIL");

        txtNombre = new TextField();
        txtNombre.setPromptText("Ingrese su nombre");

        txtCarrera = new TextField();
        txtCarrera.setPromptText("Ingrese su carrera");

        txtSemestre = new TextField();
        txtSemestre.setPromptText("Ingrese su semestre");

        txtVideojuego = new TextField();
        txtVideojuego.setPromptText("Ingrese tu videojuego favorito");

        btnMostrar = new Button("Mostrar Perfil");
        btnLimpiar = new Button("Limpiar");
        lblResultado = new Label();

        contenedor = new VBox(10);
        contenedor.setPadding(new Insets(20));
        contenedor.setAlignment(Pos.CENTER);
        contenedor.getChildren().addAll(
                titulo,
                txtNombre,
                txtCarrera,
                txtSemestre,
                txtVideojuego,
                btnMostrar,
                btnLimpiar,
                lblResultado
        );
    }

    public VBox getContenedor() { return contenedor; }

    public TextField getTxtNombre(){
        return txtNombre;
    }
    public TextField getTxtCarrera(){
        return txtCarrera;
    }
    public TextField getTxtSemestre(){
        return txtSemestre;
    }
    public TextField getTxtVideojuego(){
        return txtVideojuego;
    }
    public Button getBtnMostrar(){
        return btnMostrar;
    }
    public Button getBtnLimpiar(){
        return btnLimpiar;
    }
    public Label getLblResultado(){
        return lblResultado;
    }
}