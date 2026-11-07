package vallegrande.edu.pe.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InventoryMath {
    private InventoryMath() {}

    public static BigDecimal tonnesToKilograms(BigDecimal tonnes) {
        if (tonnes == null || tonnes.signum() <= 0) throw new IllegalArgumentException("Las toneladas deben ser mayores que cero");
        return tonnes.multiply(new BigDecimal("1000")).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal available(BigDecimal physical, BigDecimal reserved) {
        return physical.subtract(reserved);
    }

    public static void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(label + " debe ser mayor que cero");
    }
}
