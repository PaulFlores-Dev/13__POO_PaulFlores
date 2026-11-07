package vallegrande.edu.pe.model;

public record DbUser(long id, String username, String fullName, String role, String status) {
    @Override
    public String toString() {
        return fullName + " · " + username + " (" + role + ")";
    }
}

