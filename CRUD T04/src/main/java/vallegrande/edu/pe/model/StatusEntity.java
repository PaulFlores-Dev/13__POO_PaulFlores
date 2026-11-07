package vallegrande.edu.pe.model;

public enum StatusEntity {
    CATEGORIES("categories", "category_id", "Catálogos"),
    PRODUCTS("products", "product_id", "Catálogos"),
    MEMBERS("members", "member_id", "Productores"),
    COMPANIES("companies", "company_id", "Clientes"),
    USERS("users", "user_id", "Usuarios");

    private final String table;
    private final String idColumn;
    private final String module;

    StatusEntity(String table, String idColumn, String module) {
        this.table = table;
        this.idColumn = idColumn;
        this.module = module;
    }

    public String table() { return table; }
    public String idColumn() { return idColumn; }
    public String module() { return module; }
}
