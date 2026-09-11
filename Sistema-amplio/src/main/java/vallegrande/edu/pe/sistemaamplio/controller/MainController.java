package vallegrande.edu.pe.sistemaamplio.controller;

import vallegrande.edu.pe.sistemaamplio.view.MainView;

public class MainController {

    private MainView view;

    public MainController(MainView view){
        this.view = view;
        configurarEventos();
    }

    private void configurarEventos(){
        view.getBtnInicio().setOnAction(e-> {
            view.mostrarInicio();
        });

        view.getBtnUsuarios().setOnAction(e->{
            view.mostrarUsuarios();
        });

        view.getBtnProductos().setOnAction(e->{
            view.mostrarProductos();
        });

        view.getBtnReportes().setOnAction(e->{
            view.mostrarReportes();
        });

        view.getBtnConfiguracion().setOnAction(e->{
            view.mostrarConfiguracion();
        });

        view.getBtnCitas().setOnAction(e->{
            view.mostrarCitas();
        });
    }
}