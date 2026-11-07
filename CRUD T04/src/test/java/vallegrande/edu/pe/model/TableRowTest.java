package vallegrande.edu.pe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TableRowTest {
    @Test
    void exposesSqlNullValuesWithoutFailingAndPreservesColumnOrder() {
        TableRow row = new TableRow()
                .put("id", 7L)
                .put("Empresa", null)
                .put("Estado", "active");

        assertEquals(3, row.values().size());
        assertNull(row.values().get("Empresa"));
        assertEquals("id", row.values().keySet().iterator().next());
        assertThrows(UnsupportedOperationException.class, () -> row.values().put("otro", 1));
    }
}
