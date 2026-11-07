package vallegrande.edu.pe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserSessionTest {
    @Test
    void administratorCanAccessEveryModule() {
        UserSession session = session("admin");
        assertTrue(session.canAccess("Usuarios"));
        assertTrue(session.canAccess("Ventas"));
        assertTrue(session.canAccess("Inventario"));
        assertTrue(session.canAccess("Noticias"));
    }

    @Test
    void eachOperationalRoleOnlyAccessesItsWorkArea() {
        UserSession sales = session("sales");
        assertTrue(sales.canAccess("Cotizaciones"));
        assertTrue(sales.canAccess("Ventas"));
        assertFalse(sales.canAccess("Inventario"));

        UserSession warehouse = session("warehouse");
        assertTrue(warehouse.canAccess("Acopio"));
        assertTrue(warehouse.canAccess("Despachos"));
        assertFalse(warehouse.canAccess("Ventas"));

        UserSession editor = session("editor");
        assertTrue(editor.canAccess("Noticias"));
        assertFalse(editor.canAccess("Usuarios"));
    }

    private static UserSession session(String role) {
        return new UserSession(new DbUser(1, role, "Usuario " + role, role, "active"));
    }
}
