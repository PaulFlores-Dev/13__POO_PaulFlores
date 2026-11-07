package vallegrande.edu.pe.model;

import java.math.BigDecimal;

public record ReservationOption(long reservationId, long lotId, String product,
                                BigDecimal agreedKg, BigDecimal deliveredKg, BigDecimal pendingKg) {
    @Override
    public String toString() {
        return "Reserva #" + reservationId + " · lote #" + lotId + " · " + product + " · pendiente " + pendingKg + " kg";
    }
}

