package vallegrande.edu.pe.model;

public record LookupItem(long id, String label) {
    @Override
    public String toString() {
        return label;
    }
}

