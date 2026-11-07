package vallegrande.edu.pe.repository.jdbc;

import vallegrande.edu.pe.config.ConnectionProvider;
import vallegrande.edu.pe.exception.BusinessException;
import vallegrande.edu.pe.exception.PersistenceException;
import vallegrande.edu.pe.model.DbUser;
import vallegrande.edu.pe.model.LookupItem;
import vallegrande.edu.pe.model.StatusEntity;
import vallegrande.edu.pe.model.TableRow;
import vallegrande.edu.pe.model.UserSession;
import vallegrande.edu.pe.repository.ApraqRepository;
import vallegrande.edu.pe.security.PasswordHasher;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class JdbcApraqRepository implements ApraqRepository {
    private static final Set<String> ROLES = Set.of("admin", "sales", "warehouse", "editor");
    private static final Set<String> USER_STATUSES = Set.of("active", "inactive");
    private static final List<InitialUser> INITIAL_USERS = List.of(
            new InitialUser("admin", "admin123", "Administración", "APRAQ", "admin"),
            new InitialUser("sales", "sales123", "Equipo", "Ventas", "sales"),
            new InitialUser("warehouse", "warehouse123", "Equipo", "Almacén", "warehouse"),
            new InitialUser("editor", "editor123", "Equipo", "Editorial", "editor")
    );
    private final ConnectionProvider connections;

    public JdbcApraqRepository(ConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void ensureInitialUsers() {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                for (InitialUser account : INITIAL_USERS) {
                    if (userExists(connection, account.username())) continue;
                    long personId = insertKey(connection,
                            "INSERT INTO people(first_name,last_name) VALUES (?,?)",
                            statement -> {
                                statement.setString(1, account.firstName());
                                statement.setString(2, account.lastName());
                            });
                    long contactId = insertKey(connection,
                            "INSERT INTO contacts(person_id,email) VALUES (?,?)",
                            statement -> {
                                statement.setLong(1, personId);
                                statement.setString(2, account.username() + "@cuentas.apraq.local");
                            });
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users(username,password_hash,contact_id,role,status) VALUES (?,?,?,?, 'active')")) {
                        statement.setString(1, account.username());
                        statement.setString(2, PasswordHasher.hash(account.password()));
                        statement.setLong(3, contactId);
                        statement.setString(4, account.role());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            if (exception instanceof PersistenceException persistenceException) throw persistenceException;
            if (exception instanceof SQLException sqlException) {
                throw failure("No se pudieron preparar las cuentas iniciales", sqlException);
            }
            throw new PersistenceException("No se pudieron preparar las cuentas iniciales.", exception);
        }
    }

    @Override
    public Optional<DbUser> authenticate(String username, String password) {
        String sql = """
                SELECT u.user_id,u.username,u.password_hash,u.role,u.status,
                       CONCAT(p.first_name,' ',p.last_name) full_name
                FROM users u
                JOIN contacts c ON c.contact_id=u.contact_id
                JOIN people p ON p.person_id=c.person_id
                WHERE u.username=?
                """;
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeUsername(username));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || !"active".equals(rs.getString("status"))
                        || !PasswordHasher.matches(password, rs.getString("password_hash"))) {
                    return Optional.empty();
                }
                return Optional.of(new DbUser(
                        rs.getLong("user_id"), rs.getString("username"), rs.getString("full_name"),
                        rs.getString("role"), rs.getString("status")));
            }
        } catch (SQLException exception) {
            throw failure("No se pudo validar el acceso", exception);
        }
    }

    @Override
    public Map<String, BigDecimal> dashboard() {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM quote_requests WHERE status IN ('new','reviewing')) requests,
                  (SELECT COUNT(*) FROM quotations WHERE status IN ('pending','in_negotiation')) quotations,
                  (SELECT COUNT(*) FROM sales WHERE status IN ('confirmed','partially_dispatched')) sales,
                  COALESCE((SELECT SUM(CASE movement_type WHEN 'receipt' THEN quantity_kg WHEN 'adjustment_in' THEN quantity_kg ELSE -quantity_kg END) FROM inventory_movements),0) physical,
                  COALESCE((SELECT SUM(sd.quantity_kg - COALESCE(dd.delivered,0)) FROM sale_details sd JOIN inventory_reservations ir ON ir.sale_detail_id=sd.sale_detail_id AND ir.status='active' LEFT JOIN (SELECT inventory_reservation_id,SUM(quantity_kg) delivered FROM dispatch_details GROUP BY inventory_reservation_id) dd ON dd.inventory_reservation_id=ir.inventory_reservation_id),0) reserved
                """;
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            Map<String, BigDecimal> result = new LinkedHashMap<>();
            result.put("requests", rs.getBigDecimal("requests"));
            result.put("quotations", rs.getBigDecimal("quotations"));
            result.put("sales", rs.getBigDecimal("sales"));
            result.put("physical", rs.getBigDecimal("physical"));
            result.put("reserved", rs.getBigDecimal("reserved"));
            result.put("available", rs.getBigDecimal("physical").subtract(rs.getBigDecimal("reserved")));
            return result;
        } catch (SQLException exception) {
            throw failure("No se pudieron calcular los indicadores", exception);
        }
    }

    @Override
    public List<DbUser> users() {
        String sql = """
                SELECT u.user_id,u.username,u.role,u.status,CONCAT(p.first_name,' ',p.last_name) full_name
                FROM users u JOIN contacts c ON c.contact_id=u.contact_id JOIN people p ON p.person_id=c.person_id
                ORDER BY u.status='active' DESC,p.first_name,p.last_name
                """;
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<DbUser> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new DbUser(rs.getLong(1), rs.getString(2), rs.getString(5), rs.getString(3), rs.getString(4)));
            }
            return result;
        } catch (SQLException exception) {
            throw failure("No se pudieron consultar los usuarios", exception);
        }
    }

    @Override
    public List<LookupItem> lookup(String type) {
        String sql = switch (type) {
            case "categories" -> "SELECT category_id,name FROM categories WHERE status='active' ORDER BY name";
            case "products" -> "SELECT p.product_id,REPLACE(p.name,'_',' ') FROM products p JOIN categories c ON c.category_id=p.category_id WHERE p.status='active' AND c.status='active' ORDER BY p.name";
            case "members" -> "SELECT member_id,CONCAT(first_name,' ',last_name,' · DNI ',national_id_number) FROM members WHERE status='active' ORDER BY last_name,first_name";
            case "campaigns" -> "SELECT campaign_id,CONCAT(name,' · ',start_date,' / ',end_date) FROM campaigns WHERE status IN ('planned','active') ORDER BY start_date DESC";
            case "companies" -> "SELECT company_id,CONCAT(business_name,' · RUC ',ruc) FROM companies WHERE status='active' ORDER BY business_name";
            case "people" -> "SELECT person_id,CONCAT(first_name,' ',last_name) FROM people ORDER BY last_name,first_name";
            case "contacts" -> "SELECT c.contact_id,CONCAT(p.first_name,' ',p.last_name,COALESCE(CONCAT(' · ',co.business_name),'')) FROM contacts c JOIN people p ON p.person_id=c.person_id LEFT JOIN companies co ON co.company_id=c.company_id ORDER BY p.last_name,p.first_name";
            case "locations" -> "SELECT CAST(location_code AS UNSIGNED),CONCAT(district_name,', ',province_name,' · ',location_code) FROM geographic_locations ORDER BY department_name,province_name,district_name";
            case "lots" -> "SELECT l.collection_lot_id,CONCAT('#',l.collection_lot_id,' · ',REPLACE(p.name,'_',' '),' · ',m.first_name,' ',m.last_name) FROM collection_lots l JOIN products p ON p.product_id=l.product_id JOIN members m ON m.member_id=l.member_id ORDER BY l.collection_lot_id DESC";
            case "sales" -> "SELECT s.sale_id,CONCAT('#',s.sale_id,' · ',c.business_name,' · ',s.status) FROM sales s JOIN companies c ON c.company_id=s.company_id WHERE s.status IN ('confirmed','partially_dispatched') ORDER BY s.sale_id DESC";
            case "requests" -> "SELECT quote_request_id,CONCAT('#',quote_request_id,' · ',first_name,' ',last_name,' · ',status) FROM quote_requests WHERE status IN ('new','reviewing') ORDER BY submitted_at DESC";
            case "accepted_quotations" -> "SELECT q.quotation_id,CONCAT('#',q.quotation_id,' · ',c.business_name,' · válida ',q.valid_until) FROM quotations q JOIN companies c ON c.company_id=q.company_id WHERE q.status='accepted' ORDER BY q.quotation_id DESC";
            default -> throw new IllegalArgumentException("Catálogo no reconocido: " + type);
        };
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<LookupItem> result = new ArrayList<>();
            while (rs.next()) result.add(new LookupItem(rs.getLong(1), rs.getString(2)));
            return result;
        } catch (SQLException exception) {
            throw failure("No se pudo cargar el catálogo " + type, exception);
        }
    }

    @Override public List<TableRow> requestLines(long requestId) {
        return parameterRows("SELECT d.quote_request_detail_id,d.quote_request_id,d.product_id,REPLACE(p.name,'_',' '),d.quantity_kg,d.packaging_preference FROM quote_request_details d JOIN products p ON p.product_id=d.product_id WHERE d.quote_request_id=? ORDER BY d.quote_request_detail_id", requestId,
                "id","request_id","product_id","Producto","Solicitado kg","Presentación");
    }

    @Override public List<TableRow> quotationLines(long quotationId) {
        return parameterRows("SELECT d.quotation_detail_id,d.quotation_id,d.collection_lot_id,REPLACE(p.name,'_',' '),d.quantity_kg,d.unit_price FROM quotation_details d JOIN products p ON p.product_id=d.product_id WHERE d.quotation_id=? ORDER BY d.quotation_detail_id", quotationId,
                "id","quotation_id","lot_id","Producto","Cantidad kg","Precio S/");
    }

    private List<TableRow> parameterRows(String sql,long id,String...keys){
        try(Connection connection=connections.open();PreparedStatement statement=connection.prepareStatement(sql)){statement.setLong(1,id);try(ResultSet rs=statement.executeQuery()){List<TableRow>out=new ArrayList<>();while(rs.next()){TableRow row=new TableRow();for(int i=0;i<keys.length;i++)row.put(keys[i],rs.getObject(i+1));out.add(row);}return out;}}catch(SQLException exception){throw failure("No se pudieron cargar las líneas",exception);}
    }

    @Override
    public List<TableRow> rows(String view) {
        Query query = queryFor(view);
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(query.sql());
             ResultSet rs = statement.executeQuery()) {
            List<TableRow> result = new ArrayList<>();
            while (rs.next()) {
                TableRow row = new TableRow();
                for (int i = 0; i < query.keys().length; i++) row.put(query.keys()[i], rs.getObject(i + 1));
                result.add(row);
            }
            return result;
        } catch (SQLException exception) {
            throw failure("No se pudo consultar " + view, exception);
        }
    }

    private Query queryFor(String view) {
        return switch (view) {
            case "categories" -> new Query("SELECT category_id,name,status FROM categories ORDER BY name", "id", "Nombre", "Estado");
            case "products" -> new Query("SELECT p.product_id,p.name,c.name,p.status,p.category_id FROM products p JOIN categories c ON c.category_id=p.category_id ORDER BY p.name", "id", "Producto", "Categoría", "Estado", "category_id");
            case "locations" -> new Query("SELECT location_code,department_name,province_name,district_name FROM geographic_locations ORDER BY department_name,province_name,district_name", "Código", "Departamento", "Provincia", "Distrito");
            case "members" -> new Query("SELECT member_id,national_id_number,first_name,last_name,phone_number,entry_date,address,status FROM members ORDER BY last_name,first_name", "id", "DNI", "Nombres", "Apellidos", "Teléfono", "Ingreso", "Dirección", "Estado");
            case "campaigns" -> new Query("SELECT campaign_id,name,start_date,end_date,status FROM campaigns ORDER BY start_date DESC", "id", "Campaña", "Inicio", "Fin", "Estado");
            case "companies" -> new Query("SELECT company_id,business_name,ruc,status,created_at FROM companies ORDER BY business_name", "id", "Razón social", "RUC", "Estado", "Registro");
            case "people" -> new Query("SELECT person_id,first_name,last_name,document_type,document_number FROM people ORDER BY last_name,first_name", "id", "Nombres", "Apellidos", "Tipo doc.", "Documento");
            case "contacts" -> new Query("SELECT c.contact_id,CONCAT(p.first_name,' ',p.last_name),co.business_name,c.phone_number,c.email,c.address,c.location_code,c.person_id,c.company_id FROM contacts c JOIN people p ON p.person_id=c.person_id LEFT JOIN companies co ON co.company_id=c.company_id ORDER BY p.last_name,p.first_name", "id", "Persona", "Empresa", "Teléfono", "Correo", "Dirección", "Ubicación", "person_id", "company_id");
            case "requests" -> new Query("SELECT q.quote_request_id,CONCAT(q.first_name,' ',q.last_name),q.company_name,q.phone_number,q.email,q.delivery_address,q.message,GROUP_CONCAT(CONCAT(REPLACE(p.name,'_',' '),' · ',d.quantity_kg,' kg',COALESCE(CONCAT(' · ',d.packaging_preference),'')) SEPARATOR '\\n'),q.submitted_at,q.status,u.username,q.assigned_user_id FROM quote_requests q JOIN quote_request_details d ON d.quote_request_id=q.quote_request_id JOIN products p ON p.product_id=d.product_id LEFT JOIN users u ON u.user_id=q.assigned_user_id GROUP BY q.quote_request_id ORDER BY q.submitted_at DESC", "id", "Contacto", "Empresa informada", "Teléfono", "Correo", "Entrega", "Mensaje", "Productos", "Fecha", "Estado", "Responsable", "assigned_user_id");
            case "quotations" -> new Query("SELECT q.quotation_id,CONCAT(p.first_name,' ',p.last_name),c.business_name,q.quotation_date,q.valid_until,q.delivery_address,q.status,COUNT(d.quotation_detail_id),SUM(d.quantity_kg*d.unit_price),q.quote_request_id FROM quotations q JOIN contacts ct ON ct.contact_id=q.contact_id JOIN people p ON p.person_id=ct.person_id LEFT JOIN companies c ON c.company_id=q.company_id JOIN quotation_details d ON d.quotation_id=q.quotation_id GROUP BY q.quotation_id ORDER BY q.quotation_id DESC", "id", "Contacto", "Empresa", "Fecha", "Válida hasta", "Entrega", "Estado", "Líneas", "Total S/", "Solicitud");
            case "sales" -> new Query("SELECT s.sale_id,c.business_name,c.ruc,s.sale_date,s.status,s.payment_method,s.delivery_address,COUNT(sd.sale_detail_id),SUM(sd.quantity_kg*sd.unit_price),s.operation_key FROM sales s JOIN companies c ON c.company_id=s.company_id JOIN sale_details sd ON sd.sale_id=s.sale_id GROUP BY s.sale_id ORDER BY s.sale_id DESC", "id", "Cliente", "RUC", "Fecha", "Estado", "Pago", "Entrega", "Líneas", "Total S/", "operation_key");
            case "lots" -> new Query("SELECT l.collection_lot_id,CONCAT(m.first_name,' ',m.last_name),REPLACE(p.name,'_',' '),c.name,l.entry_date,l.total_weight_kg,u.username FROM collection_lots l JOIN members m ON m.member_id=l.member_id JOIN products p ON p.product_id=l.product_id JOIN campaigns c ON c.campaign_id=l.campaign_id JOIN users u ON u.user_id=l.received_by_user_id ORDER BY l.collection_lot_id DESC", "id", "Productor", "Producto", "Campaña", "Ingreso", "Peso original kg", "Registró");
            case "inventory" -> new Query("SELECT l.collection_lot_id,REPLACE(p.name,'_',' '),CONCAT(me.first_name,' ',me.last_name),COALESCE(SUM(CASE im.movement_type WHEN 'receipt' THEN im.quantity_kg WHEN 'adjustment_in' THEN im.quantity_kg ELSE -im.quantity_kg END),0),COALESCE(r.reserved,0),COALESCE(SUM(CASE im.movement_type WHEN 'receipt' THEN im.quantity_kg WHEN 'adjustment_in' THEN im.quantity_kg ELSE -im.quantity_kg END),0)-COALESCE(r.reserved,0) FROM collection_lots l JOIN products p ON p.product_id=l.product_id JOIN members me ON me.member_id=l.member_id LEFT JOIN inventory_movements im ON im.collection_lot_id=l.collection_lot_id LEFT JOIN (SELECT ir.collection_lot_id,SUM(sd.quantity_kg-COALESCE(dd.qty,0)) reserved FROM inventory_reservations ir JOIN sale_details sd ON sd.sale_detail_id=ir.sale_detail_id LEFT JOIN (SELECT inventory_reservation_id,SUM(quantity_kg) qty FROM dispatch_details GROUP BY inventory_reservation_id) dd ON dd.inventory_reservation_id=ir.inventory_reservation_id WHERE ir.status='active' GROUP BY ir.collection_lot_id) r ON r.collection_lot_id=l.collection_lot_id GROUP BY l.collection_lot_id ORDER BY l.collection_lot_id DESC", "id", "Producto", "Productor", "Físico kg", "Reservado kg", "Disponible kg");
            case "movements" -> new Query("SELECT im.inventory_movement_id,im.collection_lot_id,REPLACE(p.name,'_',' '),im.movement_type,im.quantity_kg,im.movement_at,im.reason,u.username,im.operation_key FROM inventory_movements im JOIN collection_lots l ON l.collection_lot_id=im.collection_lot_id JOIN products p ON p.product_id=l.product_id JOIN users u ON u.user_id=im.recorded_by_user_id ORDER BY im.movement_at DESC", "id", "Lote", "Producto", "Tipo", "Cantidad kg", "Fecha UTC", "Motivo", "Usuario", "operation_key");
            case "reservations" -> new Query("SELECT ir.inventory_reservation_id,ir.sale_id,ir.collection_lot_id,sd.quantity_kg,COALESCE(SUM(dd.quantity_kg),0),sd.quantity_kg-COALESCE(SUM(dd.quantity_kg),0),ir.status,ir.reserved_at FROM inventory_reservations ir JOIN sale_details sd ON sd.sale_detail_id=ir.sale_detail_id LEFT JOIN dispatch_details dd ON dd.inventory_reservation_id=ir.inventory_reservation_id GROUP BY ir.inventory_reservation_id ORDER BY ir.inventory_reservation_id DESC", "id", "Venta", "Lote", "Acordado kg", "Despachado kg", "Pendiente kg", "Estado", "Reserva UTC");
            case "dispatches" -> new Query("SELECT d.dispatch_id,d.sale_id,c.business_name,d.dispatched_at,d.delivery_reference,SUM(dd.quantity_kg),u.username,d.operation_key FROM dispatches d JOIN sales s ON s.sale_id=d.sale_id JOIN companies c ON c.company_id=s.company_id JOIN dispatch_details dd ON dd.dispatch_id=d.dispatch_id JOIN users u ON u.user_id=d.registered_by_user_id GROUP BY d.dispatch_id ORDER BY d.dispatched_at DESC", "id", "Venta", "Cliente", "Despacho UTC", "Referencia", "Cantidad kg", "Registró", "operation_key");
            case "news" -> new Query("SELECT n.news_id,n.title,n.description,n.photos,n.status,n.published_at,u.username FROM news n JOIN users u ON u.user_id=n.author_user_id ORDER BY n.created_at DESC", "id", "Título", "Contenido", "Fotos JSON", "Estado", "Publicación UTC", "Autor");
            case "users" -> new Query("SELECT u.user_id,u.username,CONCAT(p.first_name,' ',p.last_name),u.role,u.status,u.created_at,u.contact_id FROM users u JOIN contacts c ON c.contact_id=u.contact_id JOIN people p ON p.person_id=c.person_id ORDER BY u.username", "id", "Usuario", "Persona", "Rol", "Estado", "Registro UTC", "contact_id");
            default -> throw new IllegalArgumentException("Vista no reconocida: " + view);
        };
    }

    @Override
    public void setActive(StatusEntity entity, long id, boolean active, UserSession actor) {
        if (entity == null || id <= 0) throw new IllegalArgumentException("Seleccione un registro válido");
        if (actor == null || !actor.canAccess(entity.module())) {
            throw new BusinessException("No tiene permiso para cambiar el estado de este registro");
        }
        if (entity == StatusEntity.USERS && id == actor.userId() && !active) {
            throw new BusinessException("No puede desactivar su propia cuenta");
        }
        // Los identificadores SQL provienen exclusivamente del enum; los datos se parametrizan.
        String sql = "UPDATE " + entity.table() + " SET status=? WHERE " + entity.idColumn() + "=? AND status=?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, active ? "active" : "inactive");
            statement.setLong(2, id);
            statement.setString(3, active ? "inactive" : "active");
            if (statement.executeUpdate() != 1) {
                throw new BusinessException("El registro ya cambió de estado o no existe. Actualice la tabla.");
            }
        } catch (SQLException exception) {
            throw failure("No se pudo cambiar el estado del registro", exception);
        }
    }

    @Override public void saveCategory(Long id, String name, String status) {
        upsert(id, "INSERT INTO categories(name,status) VALUES (?,?)", "UPDATE categories SET name=?,status=? WHERE category_id=?", ps -> { ps.setString(1, required(name,"nombre")); ps.setString(2,status); });
    }

    @Override public void saveProduct(Long id, String name, long categoryId, String status) {
        upsert(id, "INSERT INTO products(name,category_id,status) VALUES (?,?,?)", "UPDATE products SET name=?,category_id=?,status=? WHERE product_id=?", ps -> { ps.setString(1, required(name,"nombre")); ps.setLong(2,categoryId); ps.setString(3,status); });
    }

    @Override public void saveMember(Long id, String dni, String firstName, String lastName, String phone, LocalDate entryDate, String address, String status) {
        upsert(id, "INSERT INTO members(national_id_number,first_name,last_name,phone_number,entry_date,address,status) VALUES (?,?,?,?,?,?,?)", "UPDATE members SET national_id_number=?,first_name=?,last_name=?,phone_number=?,entry_date=?,address=?,status=? WHERE member_id=?", ps -> { ps.setString(1,dni); ps.setString(2,required(firstName,"nombres")); ps.setString(3,required(lastName,"apellidos")); nullable(ps,4,phone); ps.setObject(5,entryDate); nullable(ps,6,address); ps.setString(7,status); });
    }

    @Override public void saveCampaign(Long id, String name, LocalDate start, LocalDate end, String status) {
        if (end.isBefore(start)) throw new IllegalArgumentException("La fecha final no puede ser anterior al inicio");
        upsert(id, "INSERT INTO campaigns(name,start_date,end_date,status) VALUES (?,?,?,?)", "UPDATE campaigns SET name=?,start_date=?,end_date=?,status=? WHERE campaign_id=?", ps -> { ps.setString(1,required(name,"nombre")); ps.setObject(2,start); ps.setObject(3,end); ps.setString(4,status); });
    }

    @Override public void saveCompany(Long id, String businessName, String ruc, String status) {
        if (!ruc.matches("\\d{11}")) throw new IllegalArgumentException("El RUC debe tener 11 dígitos");
        upsert(id, "INSERT INTO companies(business_name,ruc,status) VALUES (?,?,?)", "UPDATE companies SET business_name=?,ruc=?,status=? WHERE company_id=?", ps -> { ps.setString(1,required(businessName,"razón social")); ps.setString(2,ruc); ps.setString(3,status); });
    }

    @Override public void savePerson(Long id, String firstName, String lastName, String documentType, String documentNumber) {
        if ((blank(documentType)) != (blank(documentNumber))) throw new IllegalArgumentException("Tipo y número de documento deben indicarse juntos");
        upsert(id, "INSERT INTO people(first_name,last_name,document_type,document_number) VALUES (?,?,?,?)", "UPDATE people SET first_name=?,last_name=?,document_type=?,document_number=? WHERE person_id=?", ps -> { ps.setString(1,required(firstName,"nombres")); ps.setString(2,required(lastName,"apellidos")); nullable(ps,3,documentType); nullable(ps,4,documentNumber); });
    }

    @Override public void saveContact(Long id, long personId, Long companyId, String phone, String email, String address, String locationCode) {
        if (blank(phone) && blank(email)) throw new IllegalArgumentException("Indique teléfono o correo");
        upsert(id, "INSERT INTO contacts(person_id,company_id,phone_number,email,address,location_code) VALUES (?,?,?,?,?,?)", "UPDATE contacts SET person_id=?,company_id=?,phone_number=?,email=?,address=?,location_code=? WHERE contact_id=?", ps -> { ps.setLong(1,personId); nullableLong(ps,2,companyId); nullable(ps,3,phone); nullable(ps,4,email); nullable(ps,5,address); nullable(ps,6,locationCode); });
    }

    @Override public void updateRequest(long requestId, Long assignedUserId, String status) {
        execute("UPDATE quote_requests SET assigned_user_id=?,status=? WHERE quote_request_id=?", ps -> { nullableLong(ps,1,assignedUserId); ps.setString(2,status); ps.setLong(3,requestId); });
    }

    @Override public void updateQuotationStatus(long quotationId,String status){
        execute("UPDATE quotations SET status=? WHERE quotation_id=?",ps->{ps.setString(1,status);ps.setLong(2,quotationId);});
    }

    @Override public void saveNews(Long id, String title, String description, String photosJson, String status, long authorUserId) {
        boolean published = status.equals("published") || status.equals("archived");
        String insert = "INSERT INTO news(title,description,photos,status,published_at,author_user_id) VALUES (?,?,?,?," + (published ? "UTC_TIMESTAMP(6)" : "NULL") + ",?)";
        String update = "UPDATE news SET title=?,description=?,photos=?,status=?,published_at=" + (published ? "COALESCE(published_at,UTC_TIMESTAMP(6))" : "NULL") + ",author_user_id=? WHERE news_id=?";
        upsert(id, insert, update, ps -> { ps.setString(1,required(title,"título")); ps.setString(2,required(description,"contenido")); nullable(ps,3,photosJson); ps.setString(4,status); ps.setLong(5,authorUserId); });
    }

    @Override
    public void saveUser(Long id, String username, String password, long contactId, String role, String status) {
        String normalizedUsername = normalizeUsername(username);
        if (!normalizedUsername.matches("[a-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo");
        }
        if (!ROLES.contains(role)) throw new IllegalArgumentException("Seleccione un rol válido");
        if (!USER_STATUSES.contains(status)) throw new IllegalArgumentException("Seleccione un estado válido");
        boolean changePassword = password != null && !password.isBlank();
        if (id == null || changePassword) PasswordHasher.validate(password);

        if (id == null) {
            execute("INSERT INTO users(username,password_hash,contact_id,role,status) VALUES (?,?,?,?,?)", statement -> {
                statement.setString(1, normalizedUsername);
                statement.setString(2, PasswordHasher.hash(password));
                statement.setLong(3, contactId);
                statement.setString(4, role);
                statement.setString(5, status);
            });
        } else if (changePassword) {
            execute("UPDATE users SET username=?,password_hash=?,contact_id=?,role=?,status=? WHERE user_id=?", statement -> {
                statement.setString(1, normalizedUsername);
                statement.setString(2, PasswordHasher.hash(password));
                statement.setLong(3, contactId);
                statement.setString(4, role);
                statement.setString(5, status);
                statement.setLong(6, id);
            });
        } else {
            execute("UPDATE users SET username=?,contact_id=?,role=?,status=? WHERE user_id=?", statement -> {
                statement.setString(1, normalizedUsername);
                statement.setLong(2, contactId);
                statement.setString(3, role);
                statement.setString(4, status);
                statement.setLong(5, id);
            });
        }
    }

    private void upsert(Long id, String insertSql, String updateSql, SqlBinder binder) {
        String sql = id == null ? insertSql : updateSql;
        execute(sql, ps -> { binder.bind(ps); if (id != null) ps.setLong(ps.getParameterMetaData().getParameterCount(), id); });
    }

    private void execute(String sql, SqlBinder binder) {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) throw new SQLException("La operación no afectó exactamente una fila");
        } catch (SQLException exception) {
            throw failure("No se pudo guardar la información", exception);
        }
    }

    private static void nullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (blank(value)) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value.trim());
    }

    private static void nullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) ps.setNull(index, Types.BIGINT); else ps.setLong(index, value);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    private static String required(String value, String label) {
        if (blank(value)) throw new IllegalArgumentException("El campo " + label + " es obligatorio");
        return value.trim();
    }

    private PersistenceException failure(String message, SQLException exception) {
        String detail = exception.getSQLState() != null && exception.getSQLState().startsWith("23")
                ? " Los datos duplican un registro o incumplen una relación existente."
                : "";
        return new PersistenceException(message + "." + detail, exception);
    }

    private static boolean userExists(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static long insertKey(Connection connection, String sql, SqlBinder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("La base de datos no devolvió el identificador creado");
                return keys.getLong(1);
            }
        }
    }

    private record Query(String sql, String... keys) {}
    private record InitialUser(String username, String password, String firstName, String lastName, String role) {}
    @FunctionalInterface private interface SqlBinder { void bind(PreparedStatement statement) throws SQLException; }
}
