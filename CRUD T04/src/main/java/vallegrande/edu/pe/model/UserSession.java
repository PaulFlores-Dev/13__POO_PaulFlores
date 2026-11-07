package vallegrande.edu.pe.model;

import java.util.Map;
import java.util.Set;

public record UserSession(DbUser user) {
    private static final Map<String, Set<String>> MODULES_BY_ROLE = Map.of(
            "sales", Set.of("Inicio", "Clientes", "Solicitudes web", "Cotizaciones", "Ventas", "Reportes"),
            "warehouse", Set.of("Inicio", "Productores", "Acopio", "Inventario", "Despachos", "Reportes"),
            "editor", Set.of("Inicio", "Noticias")
    );

    public UserSession {
        if (user == null || !"active".equals(user.status())) {
            throw new IllegalArgumentException("La sesión requiere un usuario activo");
        }
    }

    public long userId() {
        return user.id();
    }

    public String username() {
        return user.username();
    }

    public String role() {
        return user.role();
    }

    public boolean isAdmin() {
        return "admin".equals(role());
    }

    public boolean canAccess(String module) {
        return isAdmin() || MODULES_BY_ROLE.getOrDefault(role(), Set.of("Inicio")).contains(module);
    }

    public String roleLabel() {
        return switch (role()) {
            case "admin" -> "Administrador";
            case "sales" -> "Ventas";
            case "warehouse" -> "Almacén";
            case "editor" -> "Editor";
            default -> role();
        };
    }
}
