package vallegrande.edu.pe.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InventoryMathTest {
    @Test void convertsTonnesToKilogramsWithoutDoubleRounding() {
        assertEquals(new BigDecimal("1250.00"), InventoryMath.tonnesToKilograms(new BigDecimal("1.250")));
    }

    @Test void calculatesPhysicalReservedAndAvailableBalance() {
        BigDecimal receipts = new BigDecimal("800.00");
        BigDecimal positiveAdjustments = new BigDecimal("25.00");
        BigDecimal dispatches = new BigDecimal("130.00");
        BigDecimal negativeAdjustments = new BigDecimal("5.00");
        BigDecimal physical = receipts.add(positiveAdjustments).subtract(dispatches).subtract(negativeAdjustments);
        BigDecimal pendingReservation = new BigDecimal("200.00").subtract(new BigDecimal("80.00"));
        assertEquals(new BigDecimal("690.00"), physical);
        assertEquals(new BigDecimal("570.00"), InventoryMath.available(physical, pendingReservation));
    }

    @Test void rejectsNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () -> InventoryMath.tonnesToKilograms(BigDecimal.ZERO));
    }
}
