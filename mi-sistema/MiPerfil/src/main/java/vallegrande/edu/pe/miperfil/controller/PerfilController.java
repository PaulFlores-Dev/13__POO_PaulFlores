package vallegrande.edu.pe.miperfil.controller;

import vallegrande.edu.pe.miperfil.model.Perfil;
import vallegrande.edu.pe.miperfil.view.PerfilView;

public class PerfilController {
    private PerfilView view;

    public PerfilController(PerfilView view){
        this.view = view;
        view.getBtnMostrar().setOnAction(e -> mostrarPerfil());
        view.getBtnLimpiar().setOnAction(e -> limpiarCampos());
    }

    private void mostrarPerfil(){
        String nombre = view.getTxtNombre().getText();
        String carrera = view.getTxtCarrera().getText();
        String semestre = view.getTxtSemestre().getText();
        String videojuego = view.getTxtVideojuego().getText();

        if (nombre == null || nombre.trim().isEmpty()) {
            view.getLblResultado().setText("⚠ El nombre es obligatorio.");
            return;
        }

        Perfil perfil = new Perfil(
                nombre,
                carrera,
                semestre,
                videojuego
        );
        view.getLblResultado().setText(
                perfil.obtenerPresentacion()
        );
    }

    private void limpiarCampos(){
        view.getTxtNombre().clear();
        view.getTxtCarrera().clear();
        view.getTxtSemestre().clear();
        view.getTxtVideojuego().clear();
        view.getLblResultado().setText("");
    }
}