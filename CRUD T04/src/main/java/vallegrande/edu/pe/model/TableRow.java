package vallegrande.edu.pe.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TableRow {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public TableRow put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public long id(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    public Map<String, Object> values() {
        // Los ResultSet contienen NULL válidos. Map.copyOf rechaza valores null,
        // por eso conservamos una copia inmutable que sí representa SQL NULL.
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
