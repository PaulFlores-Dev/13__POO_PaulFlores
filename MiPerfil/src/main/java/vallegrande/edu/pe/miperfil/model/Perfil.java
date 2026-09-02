package vallegrande.edu.pe.miperfil.model;

public class Perfil {
    private String nombre;
    private String carrera;
    private String semestre;
    private String videojuego;

    public Perfil(String nombre, String carrera, String semestre, String videojuego){
        this.nombre = nombre;
        this.carrera = carrera;
        this.semestre = semestre;
        this.videojuego = videojuego;
    }

    public String getNombre() { return nombre; }
    public String getCarrera() { return carrera; }
    public String getSemestre() { return semestre; }
    public String getVideojuego() { return videojuego; }

    public String obtenerPresentacion(){
        return "Hola, soy " + nombre + "\nCarrera: " + carrera + "\nSemestre: " + semestre + "\nVideojuego favorito: " + videojuego;
    }
}