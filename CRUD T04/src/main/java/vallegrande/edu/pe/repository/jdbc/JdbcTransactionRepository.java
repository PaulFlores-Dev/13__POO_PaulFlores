package vallegrande.edu.pe.repository.jdbc;

import vallegrande.edu.pe.dto.Forms;
import vallegrande.edu.pe.model.ReservationOption;
import vallegrande.edu.pe.repository.TransactionRepository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class JdbcTransactionRepository implements TransactionRepository {
    @Override
    public long insertLot(Connection c, Forms.Lot f) throws SQLException {
        String sql = "INSERT INTO collection_lots(member_id,product_id,campaign_id,entry_date,total_weight_kg,received_by_user_id) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1,f.memberId()); ps.setLong(2,f.productId()); ps.setLong(3,f.campaignId());
            ps.setObject(4,f.entryDate()); ps.setBigDecimal(5,f.weightKg()); ps.setLong(6,f.userId());
            ps.executeUpdate(); return key(ps);
        }
    }

    @Override
    public void insertMovement(Connection c, long lotId, Long dispatchDetailId, String key, String type,
                               BigDecimal quantity, LocalDateTime at, String reason, long userId) throws SQLException {
        String sql = "INSERT INTO inventory_movements(collection_lot_id,dispatch_detail_id,operation_key,movement_type,quantity_kg,movement_at,reason,recorded_by_user_id) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1,lotId); nullableLong(ps,2,dispatchDetailId); ps.setString(3,key); ps.setString(4,type);
            ps.setBigDecimal(5,quantity); ps.setObject(6,at); ps.setString(7,reason); ps.setLong(8,userId); ps.executeUpdate();
        }
    }

    @Override public Long movementLotByKey(Connection c,String key)throws SQLException{return optionalLong(c,"SELECT collection_lot_id FROM inventory_movements WHERE operation_key=?",key);}

    @Override public LocalDate[] campaignDates(Connection c, long id) throws SQLException {
        try (PreparedStatement ps=c.prepareStatement("SELECT start_date,end_date FROM campaigns WHERE campaign_id=?")) {
            ps.setLong(1,id); try(ResultSet rs=ps.executeQuery()){ if(!rs.next()) throw new SQLException("Campaña inexistente"); return new LocalDate[]{rs.getObject(1,LocalDate.class),rs.getObject(2,LocalDate.class)}; }
        }
    }

    @Override public long insertQuotation(Connection c, Forms.Quotation f) throws SQLException {
        String sql="INSERT INTO quotations(quote_request_id,contact_id,company_id,quotation_date,valid_until,delivery_address,delivery_location_code,message,status,created_by_user_id) VALUES (?,?,?,?,?,?,?,?, 'pending',?)";
        try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            nullableLong(ps,1,f.requestId()); ps.setLong(2,f.contactId()); nullableLong(ps,3,f.companyId()); ps.setObject(4,f.date()); ps.setObject(5,f.validUntil()); ps.setString(6,f.address()); ps.setString(7,f.locationCode()); nullable(ps,8,f.message()); ps.setLong(9,f.userId()); ps.executeUpdate(); return key(ps);
        }
    }

    @Override public void insertQuotationLine(Connection c,long quotationId,Forms.QuoteLine f) throws SQLException{
        String sql="INSERT INTO quotation_details(quotation_id,quote_request_id,quote_request_detail_id,product_id,collection_lot_id,quantity_kg,unit_price,packaging_preference) VALUES (?,?,?,?,?,?,?,?)";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,quotationId);nullableLong(ps,2,f.requestId());nullableLong(ps,3,f.requestDetailId());ps.setLong(4,f.productId());ps.setLong(5,f.lotId());ps.setBigDecimal(6,f.quantityKg());ps.setBigDecimal(7,f.unitPrice());nullable(ps,8,f.packaging());ps.executeUpdate();}
    }

    @Override public void markRequestQuoted(Connection c,Long id)throws SQLException{if(id==null)return;try(PreparedStatement ps=c.prepareStatement("UPDATE quote_requests SET status='quoted' WHERE quote_request_id=? AND status IN ('new','reviewing')")){ps.setLong(1,id);ps.executeUpdate();}}

    @Override public long insertSale(Connection c,Forms.Sale f)throws SQLException{
        String sql="INSERT INTO sales(company_id,quotation_id,operation_key,sale_date,status,payment_method,delivery_address,delivery_location_code,delivery_conditions,user_id) VALUES (?,?,?,?,'pending',?,?,?,?,?)";
        try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,f.companyId());nullableLong(ps,2,f.quotationId());ps.setString(3,f.operationKey());ps.setObject(4,f.date());ps.setString(5,f.paymentMethod());ps.setString(6,f.address());ps.setString(7,f.locationCode());ps.setString(8,f.conditions());ps.setLong(9,f.userId());ps.executeUpdate();return key(ps);}
    }

    @Override public void insertSaleLine(Connection c,long saleId,Forms.SaleLine f)throws SQLException{
        String sql="INSERT INTO sale_details(sale_id,quotation_id,quotation_detail_id,collection_lot_id,quantity_kg,unit_price) VALUES (?,?,?,?,?,?)";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,saleId);nullableLong(ps,2,f.quotationId());nullableLong(ps,3,f.quotationDetailId());ps.setLong(4,f.lotId());ps.setBigDecimal(5,f.quantityKg());ps.setBigDecimal(6,f.unitPrice());ps.executeUpdate();}
    }

    @Override public Long existingSale(Connection c,String key)throws SQLException{return optionalLong(c,"SELECT sale_id FROM sales WHERE operation_key=?",key);}

    @Override public List<Long> saleLotIds(Connection c,long saleId)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT collection_lot_id FROM sale_details WHERE sale_id=? ORDER BY collection_lot_id")){ps.setLong(1,saleId);try(ResultSet rs=ps.executeQuery()){List<Long> out=new ArrayList<>();while(rs.next())out.add(rs.getLong(1));return out;}}
    }

    @Override public void lockLots(Connection c,List<Long> ids)throws SQLException{
        for(Long id:ids.stream().distinct().sorted().toList())try(PreparedStatement ps=c.prepareStatement("SELECT collection_lot_id FROM collection_lots WHERE collection_lot_id=? FOR UPDATE")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Lote inexistente: "+id);}}
    }

    @Override public BigDecimal physicalStock(Connection c,long lot)throws SQLException{
        String sql="SELECT COALESCE(SUM(CASE movement_type WHEN 'receipt' THEN quantity_kg WHEN 'adjustment_in' THEN quantity_kg ELSE -quantity_kg END),0) FROM inventory_movements WHERE collection_lot_id=?";return decimal(c,sql,lot);
    }

    @Override public BigDecimal pendingReserved(Connection c,long lot)throws SQLException{
        String sql="SELECT COALESCE(SUM(sd.quantity_kg-COALESCE(dd.delivered,0)),0) FROM inventory_reservations ir JOIN sale_details sd ON sd.sale_detail_id=ir.sale_detail_id LEFT JOIN (SELECT inventory_reservation_id,SUM(quantity_kg) delivered FROM dispatch_details GROUP BY inventory_reservation_id) dd ON dd.inventory_reservation_id=ir.inventory_reservation_id WHERE ir.collection_lot_id=? AND ir.status='active'";return decimal(c,sql,lot);
    }

    @Override public List<Long> saleDetailIds(Connection c,long saleId)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("SELECT sale_detail_id FROM sale_details WHERE sale_id=? ORDER BY collection_lot_id")){ps.setLong(1,saleId);try(ResultSet rs=ps.executeQuery()){List<Long>out=new ArrayList<>();while(rs.next())out.add(rs.getLong(1));return out;}}
    }

    @Override public BigDecimal saleQuantityForLot(Connection c,long sale,long lot)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT quantity_kg FROM sale_details WHERE sale_id=? AND collection_lot_id=?")){ps.setLong(1,sale);ps.setLong(2,lot);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("La venta no contiene el lote "+lot);return rs.getBigDecimal(1);}}}

    @Override public void insertReservation(Connection c,long detail,long user)throws SQLException{
        String sql="INSERT INTO inventory_reservations(sale_detail_id,sale_id,collection_lot_id,status,created_by_user_id) SELECT sale_detail_id,sale_id,collection_lot_id,'active',? FROM sale_details WHERE sale_detail_id=?";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,user);ps.setLong(2,detail);ps.executeUpdate();}
    }

    @Override public void updateSaleStatus(Connection c,long sale,String status)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE sales SET status=? WHERE sale_id=?")){ps.setString(1,status);ps.setLong(2,sale);ps.executeUpdate();}}
    @Override public String saleStatus(Connection c,long sale)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT status FROM sales WHERE sale_id=? FOR UPDATE")){ps.setLong(1,sale);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Venta inexistente");return rs.getString(1);}}}
    @Override public int dispatchCount(Connection c,long sale)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM dispatches WHERE sale_id=?")){ps.setLong(1,sale);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}}
    @Override public void releaseReservations(Connection c,long sale)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE inventory_reservations SET status='released',closed_at=UTC_TIMESTAMP(6) WHERE sale_id=? AND status='active'")){ps.setLong(1,sale);ps.executeUpdate();}}

    @Override public List<ReservationOption> pendingReservations(Connection c,long sale)throws SQLException{
        String sql="SELECT ir.inventory_reservation_id,ir.collection_lot_id,REPLACE(p.name,'_',' '),sd.quantity_kg,COALESCE(SUM(dd.quantity_kg),0),sd.quantity_kg-COALESCE(SUM(dd.quantity_kg),0) FROM inventory_reservations ir JOIN sale_details sd ON sd.sale_detail_id=ir.sale_detail_id JOIN collection_lots l ON l.collection_lot_id=ir.collection_lot_id JOIN products p ON p.product_id=l.product_id LEFT JOIN dispatch_details dd ON dd.inventory_reservation_id=ir.inventory_reservation_id WHERE ir.sale_id=? AND ir.status='active' GROUP BY ir.inventory_reservation_id ORDER BY ir.collection_lot_id";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,sale);try(ResultSet rs=ps.executeQuery()){List<ReservationOption>out=new ArrayList<>();while(rs.next())out.add(new ReservationOption(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getBigDecimal(6)));return out;}}
    }

    @Override public Long existingDispatch(Connection c,String key)throws SQLException{return optionalLong(c,"SELECT dispatch_id FROM dispatches WHERE operation_key=?",key);}
    @Override public long insertDispatch(Connection c,Forms.Dispatch f)throws SQLException{String sql="INSERT INTO dispatches(sale_id,operation_key,dispatched_at,delivery_reference,notes,registered_by_user_id) VALUES (?,?,?,?,?,?)";try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,f.saleId());ps.setString(2,f.operationKey());ps.setObject(3,f.dispatchedAt());nullable(ps,4,f.reference());nullable(ps,5,f.notes());ps.setLong(6,f.userId());ps.executeUpdate();return key(ps);}}
    @Override public BigDecimal reservationRemaining(Connection c,long id)throws SQLException{return decimal(c,"SELECT sd.quantity_kg-COALESCE(SUM(dd.quantity_kg),0) FROM inventory_reservations ir JOIN sale_details sd ON sd.sale_detail_id=ir.sale_detail_id LEFT JOIN dispatch_details dd ON dd.inventory_reservation_id=ir.inventory_reservation_id WHERE ir.inventory_reservation_id=? AND ir.status='active' GROUP BY ir.inventory_reservation_id",id);}
    @Override public long reservationSale(Connection c,long id)throws SQLException{return requiredLong(c,"SELECT sale_id FROM inventory_reservations WHERE inventory_reservation_id=?",id);}
    @Override public long reservationLot(Connection c,long id)throws SQLException{return requiredLong(c,"SELECT collection_lot_id FROM inventory_reservations WHERE inventory_reservation_id=?",id);}
    @Override public long insertDispatchDetail(Connection c,long dispatch,long sale,Forms.DispatchLine f)throws SQLException{String sql="INSERT INTO dispatch_details(dispatch_id,sale_id,inventory_reservation_id,collection_lot_id,quantity_kg) VALUES (?,?,?,?,?)";try(PreparedStatement ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){ps.setLong(1,dispatch);ps.setLong(2,sale);ps.setLong(3,f.reservationId());ps.setLong(4,f.lotId());ps.setBigDecimal(5,f.quantityKg());ps.executeUpdate();return key(ps);}}
    @Override public void fulfillReservationIfComplete(Connection c,long id)throws SQLException{String sql="UPDATE inventory_reservations ir JOIN sale_details sd ON sd.sale_detail_id=ir.sale_detail_id SET ir.status='fulfilled',ir.closed_at=UTC_TIMESTAMP(6) WHERE ir.inventory_reservation_id=? AND ir.status='active' AND sd.quantity_kg <= (SELECT COALESCE(SUM(dd.quantity_kg),0) FROM dispatch_details dd WHERE dd.inventory_reservation_id=ir.inventory_reservation_id)";try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,id);ps.executeUpdate();}}
    @Override public boolean saleHasPendingReservations(Connection c,long sale)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM inventory_reservations WHERE sale_id=? AND status='active')")){ps.setLong(1,sale);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getBoolean(1);}}}

    private static long key(PreparedStatement ps)throws SQLException{try(ResultSet rs=ps.getGeneratedKeys()){if(!rs.next())throw new SQLException("No se obtuvo el identificador generado");return rs.getLong(1);}}
    private static void nullable(PreparedStatement ps,int i,String value)throws SQLException{if(value==null||value.isBlank())ps.setNull(i,Types.VARCHAR);else ps.setString(i,value.trim());}
    private static void nullableLong(PreparedStatement ps,int i,Long value)throws SQLException{if(value==null)ps.setNull(i,Types.BIGINT);else ps.setLong(i,value);}
    private static BigDecimal decimal(Connection c,String sql,long id)throws SQLException{try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())return BigDecimal.ZERO;BigDecimal v=rs.getBigDecimal(1);return v==null?BigDecimal.ZERO:v;}}}
    private static Long optionalLong(Connection c,String sql,String value)throws SQLException{try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,value);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getLong(1):null;}}}
    private static long requiredLong(Connection c,String sql,long value)throws SQLException{try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,value);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Registro relacionado inexistente");return rs.getLong(1);}}}
}
