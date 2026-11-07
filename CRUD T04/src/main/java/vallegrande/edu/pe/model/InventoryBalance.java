package vallegrande.edu.pe.model;

import java.math.BigDecimal;

public record InventoryBalance(long lotId, String product, String producer, BigDecimal physicalKg,
                               BigDecimal reservedKg, BigDecimal availableKg) {
    public InventoryBalance {
        physicalKg = physicalKg == null ? BigDecimal.ZERO : physicalKg;
        reservedKg = reservedKg == null ? BigDecimal.ZERO : reservedKg;
        availableKg = availableKg == null ? physicalKg.subtract(reservedKg) : availableKg;
    }
}

