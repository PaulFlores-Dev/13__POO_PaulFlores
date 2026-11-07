package vallegrande.edu.pe.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Forms {
    private Forms() {}

    public record Lot(long memberId, long productId, long campaignId, LocalDate entryDate,
                      BigDecimal weightKg, long userId, String operationKey) {}

    public record QuoteLine(Long requestId, Long requestDetailId, long productId, long lotId,
                            BigDecimal quantityKg, BigDecimal unitPrice, String packaging) {}

    public record Quotation(Long requestId, long contactId, Long companyId, LocalDate date,
                            LocalDate validUntil, String address, String locationCode, String message,
                            long userId, List<QuoteLine> lines) {}

    public record SaleLine(Long quotationId, Long quotationDetailId, long lotId,
                           BigDecimal quantityKg, BigDecimal unitPrice) {}

    public record Sale(long companyId, Long quotationId, String operationKey, LocalDate date,
                       String paymentMethod, String address, String locationCode, String conditions,
                       long userId, List<SaleLine> lines) {}

    public record DispatchLine(long reservationId, long lotId, BigDecimal quantityKg) {}

    public record Dispatch(long saleId, String operationKey, LocalDateTime dispatchedAt,
                           String reference, String notes, long userId, List<DispatchLine> lines) {}
}
