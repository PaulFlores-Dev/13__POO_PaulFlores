package vallegrande.edu.pe.repository;

import vallegrande.edu.pe.dto.Forms;
import vallegrande.edu.pe.model.ReservationOption;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository {
    long insertLot(Connection c, Forms.Lot form) throws SQLException;
    void insertMovement(Connection c, long lotId, Long dispatchDetailId, String operationKey,
                        String type, BigDecimal quantity, LocalDateTime at, String reason, long userId) throws SQLException;
    Long movementLotByKey(Connection c, String operationKey) throws SQLException;
    LocalDate[] campaignDates(Connection c, long campaignId) throws SQLException;

    long insertQuotation(Connection c, Forms.Quotation form) throws SQLException;
    void insertQuotationLine(Connection c, long quotationId, Forms.QuoteLine line) throws SQLException;
    void markRequestQuoted(Connection c, Long requestId) throws SQLException;

    long insertSale(Connection c, Forms.Sale form) throws SQLException;
    void insertSaleLine(Connection c, long saleId, Forms.SaleLine line) throws SQLException;
    Long existingSale(Connection c, String operationKey) throws SQLException;
    List<Long> saleLotIds(Connection c, long saleId) throws SQLException;
    void lockLots(Connection c, List<Long> lotIds) throws SQLException;
    BigDecimal physicalStock(Connection c, long lotId) throws SQLException;
    BigDecimal pendingReserved(Connection c, long lotId) throws SQLException;
    List<Long> saleDetailIds(Connection c, long saleId) throws SQLException;
    BigDecimal saleQuantityForLot(Connection c, long saleId, long lotId) throws SQLException;
    void insertReservation(Connection c, long saleDetailId, long userId) throws SQLException;
    void updateSaleStatus(Connection c, long saleId, String status) throws SQLException;
    String saleStatus(Connection c, long saleId) throws SQLException;
    int dispatchCount(Connection c, long saleId) throws SQLException;
    void releaseReservations(Connection c, long saleId) throws SQLException;

    List<ReservationOption> pendingReservations(Connection c, long saleId) throws SQLException;
    Long existingDispatch(Connection c, String operationKey) throws SQLException;
    long insertDispatch(Connection c, Forms.Dispatch form) throws SQLException;
    BigDecimal reservationRemaining(Connection c, long reservationId) throws SQLException;
    long reservationSale(Connection c, long reservationId) throws SQLException;
    long reservationLot(Connection c, long reservationId) throws SQLException;
    long insertDispatchDetail(Connection c, long dispatchId, long saleId, Forms.DispatchLine line) throws SQLException;
    void fulfillReservationIfComplete(Connection c, long reservationId) throws SQLException;
    boolean saleHasPendingReservations(Connection c, long saleId) throws SQLException;
}
