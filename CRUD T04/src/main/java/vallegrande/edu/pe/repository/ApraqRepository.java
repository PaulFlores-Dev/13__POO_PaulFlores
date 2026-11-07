package vallegrande.edu.pe.repository;

import vallegrande.edu.pe.model.DbUser;
import vallegrande.edu.pe.model.LookupItem;
import vallegrande.edu.pe.model.StatusEntity;
import vallegrande.edu.pe.model.TableRow;
import vallegrande.edu.pe.model.UserSession;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ApraqRepository {
    Map<String, BigDecimal> dashboard();
    void ensureInitialUsers();
    Optional<DbUser> authenticate(String username, String password);
    List<DbUser> users();
    List<LookupItem> lookup(String type);
    List<TableRow> rows(String view);
    List<TableRow> requestLines(long requestId);
    List<TableRow> quotationLines(long quotationId);

    void setActive(StatusEntity entity, long id, boolean active, UserSession actor);

    void saveCategory(Long id, String name, String status);
    void saveProduct(Long id, String name, long categoryId, String status);
    void saveMember(Long id, String dni, String firstName, String lastName, String phone,
                    LocalDate entryDate, String address, String status);
    void saveCampaign(Long id, String name, LocalDate start, LocalDate end, String status);
    void saveCompany(Long id, String businessName, String ruc, String status);
    void savePerson(Long id, String firstName, String lastName, String documentType, String documentNumber);
    void saveContact(Long id, long personId, Long companyId, String phone, String email,
                     String address, String locationCode);
    void updateRequest(long requestId, Long assignedUserId, String status);
    void updateQuotationStatus(long quotationId, String status);
    void saveNews(Long id, String title, String description, String photosJson, String status, long authorUserId);
    void saveUser(Long id, String username, String password, long contactId, String role, String status);
}
