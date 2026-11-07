package vallegrande.edu.pe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import vallegrande.edu.pe.dto.Forms;
import vallegrande.edu.pe.exception.BusinessException;
import vallegrande.edu.pe.exception.PersistenceException;
import vallegrande.edu.pe.repository.TransactionRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationsServiceTest {
    private Connection connection;
    private TransactionRepository repository;
    private OperationsService service;

    @BeforeEach void setUp() throws Exception {
        connection = mock(Connection.class);
        repository = mock(TransactionRepository.class);
        service = new OperationsService(() -> connection, repository);
    }

    @Test void saleConfirmationRollsBackWhenAvailableStockIsInsufficient() throws Exception {
        when(repository.saleStatus(connection, 10)).thenReturn("pending");
        when(repository.saleLotIds(connection, 10)).thenReturn(List.of(7L));
        when(repository.physicalStock(connection, 7)).thenReturn(new BigDecimal("100"));
        when(repository.pendingReserved(connection, 7)).thenReturn(new BigDecimal("40"));
        when(repository.saleQuantityForLot(connection, 10, 7)).thenReturn(new BigDecimal("70"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.confirmSale(10, 2));
        assertTrue(error.getMessage().contains("Stock insuficiente"));
        verify(connection).rollback();
        verify(repository, never()).insertReservation(any(), anyLong(), anyLong());
    }

    @Test void partialDispatchKeepsSalePartiallyDispatchedAndUsesSameQuantityForMovement() throws Exception {
        Forms.Dispatch form = new Forms.Dispatch(10,"dispatch-key", LocalDateTime.parse("2026-10-18T12:00:00"),null,null,2,
                List.of(new Forms.DispatchLine(5,7,new BigDecimal("25.00"))));
        when(repository.saleStatus(connection,10)).thenReturn("confirmed");
        when(repository.existingDispatch(connection,"dispatch-key")).thenReturn(null);
        when(repository.reservationSale(connection,5)).thenReturn(10L);
        when(repository.reservationLot(connection,5)).thenReturn(7L);
        when(repository.reservationRemaining(connection,5)).thenReturn(new BigDecimal("60.00"));
        when(repository.physicalStock(connection,7)).thenReturn(new BigDecimal("100.00"));
        when(repository.insertDispatch(connection,form)).thenReturn(20L);
        when(repository.insertDispatchDetail(eq(connection),eq(20L),eq(10L),any())).thenReturn(30L);
        when(repository.saleHasPendingReservations(connection,10)).thenReturn(true);

        assertEquals(20,service.dispatch(form));
        verify(repository).insertMovement(connection,7L,30L,"dispatch-key:r5","dispatch",new BigDecimal("25.00"),form.dispatchedAt(),"Despacho #20",2L);
        verify(repository).updateSaleStatus(connection,10,"partially_dispatched");
        verify(connection).commit();
    }

    @Test void retryWithSameDispatchKeyDoesNotDuplicateAnything() throws Exception {
        Forms.Dispatch form = new Forms.Dispatch(10,"stable-key",LocalDateTime.now(),null,null,2,
                List.of(new Forms.DispatchLine(5,7,BigDecimal.ONE)));
        when(repository.existingDispatch(connection,"stable-key")).thenReturn(99L);
        assertEquals(99,service.dispatch(form));
        verify(repository,never()).insertDispatch(any(),any());
        verify(repository,never()).insertDispatchDetail(any(),anyLong(),anyLong(),any());
    }

    @Test void compoundOperationRollsBackWhenMovementInsertFails() throws Exception {
        Forms.Dispatch form = new Forms.Dispatch(10,"rollback-key",LocalDateTime.now(),null,null,2,
                List.of(new Forms.DispatchLine(5,7,BigDecimal.ONE)));
        when(repository.saleStatus(connection,10)).thenReturn("confirmed");
        when(repository.existingDispatch(connection,"rollback-key")).thenReturn(null);
        when(repository.reservationSale(connection,5)).thenReturn(10L);
        when(repository.reservationLot(connection,5)).thenReturn(7L);
        when(repository.reservationRemaining(connection,5)).thenReturn(BigDecimal.TEN);
        when(repository.physicalStock(connection,7)).thenReturn(BigDecimal.TEN);
        when(repository.insertDispatch(connection,form)).thenReturn(20L);
        when(repository.insertDispatchDetail(eq(connection),eq(20L),eq(10L),any())).thenReturn(30L);
        doThrow(new SQLException("simulated failure")).when(repository).insertMovement(any(),anyLong(),any(),anyString(),anyString(),any(),any(),anyString(),anyLong());
        assertThrows(PersistenceException.class,()->service.dispatch(form));
        verify(connection).rollback();
        verify(connection,never()).commit();
    }

    @Test void lotLocksAreAcquiredBeforeBalancesAreReadToSerializeConcurrentSales() throws Exception {
        when(repository.saleStatus(connection,10)).thenReturn("pending");
        when(repository.saleLotIds(connection,10)).thenReturn(List.of(9L,7L));
        when(repository.physicalStock(connection,7)).thenReturn(BigDecimal.TEN);
        when(repository.physicalStock(connection,9)).thenReturn(BigDecimal.TEN);
        when(repository.pendingReserved(connection,7)).thenReturn(BigDecimal.ZERO);
        when(repository.pendingReserved(connection,9)).thenReturn(BigDecimal.ZERO);
        when(repository.saleQuantityForLot(eq(connection),eq(10L),anyLong())).thenReturn(BigDecimal.ONE);
        when(repository.saleDetailIds(connection,10)).thenReturn(List.of(100L,101L));
        service.confirmSale(10,2);

        InOrder order=inOrder(repository);
        order.verify(repository).lockLots(connection,List.of(7L,9L));
        order.verify(repository).physicalStock(connection,7L);
        verify(repository).updateSaleStatus(connection,10,"confirmed");
    }
}
