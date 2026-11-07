package vallegrande.edu.pe.service;

import vallegrande.edu.pe.config.ConnectionFactory;
import vallegrande.edu.pe.config.ConnectionProvider;
import vallegrande.edu.pe.dto.Forms;
import vallegrande.edu.pe.exception.BusinessException;
import vallegrande.edu.pe.exception.PersistenceException;
import vallegrande.edu.pe.model.ReservationOption;
import vallegrande.edu.pe.repository.TransactionRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OperationsService {
    private final ConnectionProvider connections;
    private final TransactionRepository repository;

    public OperationsService(ConnectionFactory connections, TransactionRepository repository) {
        this((ConnectionProvider) connections, repository);
    }

    public OperationsService(ConnectionProvider connections, TransactionRepository repository) {
        this.connections = connections;
        this.repository = repository;
    }

    public long receiveLot(Forms.Lot form) {
        InventoryMath.requirePositive(form.weightKg(), "El peso");
        requireKey(form.operationKey());
        return transaction(connection -> {
            Long existing = repository.movementLotByKey(connection, form.operationKey());
            if (existing != null) return existing;
            LocalDate[] dates = repository.campaignDates(connection, form.campaignId());
            if (form.entryDate().isBefore(dates[0]) || form.entryDate().isAfter(dates[1])) {
                throw new BusinessException("La fecha de ingreso debe estar dentro del período de la campaña");
            }
            long lotId = repository.insertLot(connection, form);
            repository.insertMovement(connection, lotId, null, form.operationKey(), "receipt", form.weightKg(),
                    LocalDateTime.now(ZoneOffset.UTC), "Recepción del lote #" + lotId, form.userId());
            return lotId;
        });
    }

    public long createQuotation(Forms.Quotation form) {
        if (form.lines() == null || form.lines().isEmpty()) throw new BusinessException("La cotización requiere al menos una línea");
        if (form.validUntil().isBefore(form.date())) throw new BusinessException("La vigencia no puede terminar antes de la cotización");
        validateUniqueLots(form.lines().stream().map(Forms.QuoteLine::lotId).toList());
        form.lines().forEach(line -> { InventoryMath.requirePositive(line.quantityKg(), "La cantidad"); InventoryMath.requirePositive(line.unitPrice(), "El precio"); });
        return transaction(connection -> {
            long id = repository.insertQuotation(connection, form);
            for (Forms.QuoteLine line : form.lines()) repository.insertQuotationLine(connection, id, line);
            repository.markRequestQuoted(connection, form.requestId());
            return id;
        });
    }

    public long createSale(Forms.Sale form) {
        requireKey(form.operationKey());
        if (form.lines() == null || form.lines().isEmpty()) throw new BusinessException("La venta requiere al menos una línea");
        validateUniqueLots(form.lines().stream().map(Forms.SaleLine::lotId).toList());
        form.lines().forEach(line -> { InventoryMath.requirePositive(line.quantityKg(), "La cantidad"); InventoryMath.requirePositive(line.unitPrice(), "El precio"); });
        return transaction(connection -> {
            Long existing = repository.existingSale(connection, form.operationKey());
            if (existing != null) return existing;
            long id = repository.insertSale(connection, form);
            for (Forms.SaleLine line : form.lines()) repository.insertSaleLine(connection, id, line);
            return id;
        });
    }

    public void confirmSale(long saleId, long userId) {
        transaction(connection -> {
            String status = repository.saleStatus(connection, saleId);
            if (status.equals("confirmed")) return null;
            if (!status.equals("pending")) throw new BusinessException("Solo una venta pendiente puede confirmarse");
            List<Long> lots = repository.saleLotIds(connection, saleId).stream().distinct().sorted().toList();
            repository.lockLots(connection, lots);
            for (long lotId : lots) {
                BigDecimal physical = repository.physicalStock(connection, lotId);
                BigDecimal reserved = repository.pendingReserved(connection, lotId);
                BigDecimal required = repository.saleQuantityForLot(connection, saleId, lotId);
                if (InventoryMath.available(physical, reserved).compareTo(required) < 0) {
                    throw new BusinessException("Stock insuficiente en el lote #" + lotId + ". Disponible: " + InventoryMath.available(physical, reserved) + " kg; requerido: " + required + " kg");
                }
            }
            for (long detailId : repository.saleDetailIds(connection, saleId)) repository.insertReservation(connection, detailId, userId);
            repository.updateSaleStatus(connection, saleId, "confirmed");
            return null;
        });
    }

    public void cancelSale(long saleId) {
        transaction(connection -> {
            String status = repository.saleStatus(connection, saleId);
            if (status.equals("cancelled")) return null;
            if (!status.equals("pending") && !status.equals("confirmed")) throw new BusinessException("La venta ya tiene avance y no puede cancelarse");
            if (repository.dispatchCount(connection, saleId) > 0) throw new BusinessException("No se puede cancelar una venta con despachos");
            List<Long> lots = repository.saleLotIds(connection, saleId).stream().distinct().sorted().toList();
            repository.lockLots(connection, lots);
            repository.releaseReservations(connection, saleId);
            repository.updateSaleStatus(connection, saleId, "cancelled");
            return null;
        });
    }

    public void adjust(long lotId, BigDecimal quantity, boolean incoming, String reason, long userId, String operationKey) {
        InventoryMath.requirePositive(quantity, "La cantidad");
        if (reason == null || reason.isBlank()) throw new BusinessException("El ajuste requiere un motivo");
        requireKey(operationKey);
        transaction(connection -> {
            Long existing = repository.movementLotByKey(connection, operationKey);
            if (existing != null) return null;
            repository.lockLots(connection, List.of(lotId));
            BigDecimal physical = repository.physicalStock(connection, lotId);
            BigDecimal reserved = repository.pendingReserved(connection, lotId);
            if (!incoming && physical.subtract(quantity).compareTo(reserved) < 0) {
                throw new BusinessException("El ajuste dejaría el stock físico por debajo de las reservas pendientes");
            }
            repository.insertMovement(connection, lotId, null, operationKey, incoming ? "adjustment_in" : "adjustment_out",
                    quantity, LocalDateTime.now(ZoneOffset.UTC), reason.trim(), userId);
            return null;
        });
    }

    public List<ReservationOption> pendingReservations(long saleId) {
        try (Connection connection = connections.open()) {
            return repository.pendingReservations(connection, saleId);
        } catch (SQLException exception) {
            throw new PersistenceException("No se pudieron consultar las reservas pendientes", exception);
        }
    }

    public long dispatch(Forms.Dispatch form) {
        requireKey(form.operationKey());
        if (form.lines() == null || form.lines().isEmpty()) throw new BusinessException("El despacho requiere al menos una línea");
        validateUniqueReservations(form.lines());
        form.lines().forEach(line -> InventoryMath.requirePositive(line.quantityKg(), "La cantidad a despachar"));
        return transaction(connection -> {
            Long existing = repository.existingDispatch(connection, form.operationKey());
            if (existing != null) return existing;
            String status = repository.saleStatus(connection, form.saleId());
            if (!status.equals("confirmed") && !status.equals("partially_dispatched")) throw new BusinessException("La venta no está disponible para despacho");
            List<Long> lots = form.lines().stream().map(Forms.DispatchLine::lotId).distinct().sorted().toList();
            repository.lockLots(connection, lots);
            for (Forms.DispatchLine line : form.lines()) {
                if (repository.reservationSale(connection, line.reservationId()) != form.saleId() || repository.reservationLot(connection, line.reservationId()) != line.lotId()) {
                    throw new BusinessException("La reserva seleccionada no corresponde a esta venta y lote");
                }
                BigDecimal remaining = repository.reservationRemaining(connection, line.reservationId());
                if (remaining.compareTo(line.quantityKg()) < 0) throw new BusinessException("La cantidad supera lo pendiente de la reserva #" + line.reservationId());
                if (repository.physicalStock(connection, line.lotId()).compareTo(line.quantityKg()) < 0) throw new BusinessException("Stock físico insuficiente en el lote #" + line.lotId());
            }
            long dispatchId = repository.insertDispatch(connection, form);
            for (Forms.DispatchLine line : form.lines()) {
                long detailId = repository.insertDispatchDetail(connection, dispatchId, form.saleId(), line);
                repository.insertMovement(connection, line.lotId(), detailId, movementKey(form.operationKey(), line.reservationId()), "dispatch", line.quantityKg(), form.dispatchedAt(), "Despacho #" + dispatchId, form.userId());
                repository.fulfillReservationIfComplete(connection, line.reservationId());
            }
            repository.updateSaleStatus(connection, form.saleId(), repository.saleHasPendingReservations(connection, form.saleId()) ? "partially_dispatched" : "completed");
            return dispatchId;
        });
    }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                if (exception instanceof BusinessException business) throw business;
                if (exception instanceof IllegalArgumentException invalid) throw invalid;
                throw new PersistenceException(readableSqlMessage(exception), exception);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("No se pudo abrir la transacción con MySQL", exception);
        }
    }

    private static String readableSqlMessage(Exception exception) {
        if (exception instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("23")) {
            return "La operación contradice una relación, restricción o clave única del modelo";
        }
        return "La operación no pudo completarse y fue revertida";
    }

    private static void validateUniqueLots(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw new BusinessException("No repita el mismo lote en una operación");
    }
    private static void validateUniqueReservations(List<Forms.DispatchLine> lines) {
        Set<Long> ids = new HashSet<>();
        for (Forms.DispatchLine line : lines) if (!ids.add(line.reservationId())) throw new BusinessException("No repita la misma reserva en un despacho");
    }
    private static void requireKey(String key) { if (key == null || key.isBlank() || key.length() > 64) throw new BusinessException("La clave de operación es obligatoria y admite hasta 64 caracteres"); }
    private static String movementKey(String dispatchKey, long reservationId) {
        String suffix = ":r" + reservationId;
        int maxBase = 64 - suffix.length();
        return dispatchKey.substring(0, Math.min(dispatchKey.length(), maxBase)) + suffix;
    }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws Exception; }
}
