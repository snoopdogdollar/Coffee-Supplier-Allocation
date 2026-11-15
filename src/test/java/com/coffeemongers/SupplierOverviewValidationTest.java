package com.coffeemongers;

import com.coffeemongers.service.SupplierOverviewService;
import com.coffeemongers.model.WeeklySupplyRecord;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplierOverviewValidationTest {
    @Test void invalidSupplierAndWeekAreRejectedBeforeConnecting(){
        assertThrows(IllegalArgumentException.class,() -> new SupplierOverviewService(0));
        assertThrows(IllegalArgumentException.class,() -> new SupplierOverviewService(1).load(LocalDate.of(0,6,1)));
        assertThrows(IllegalArgumentException.class,() -> SupplierOverviewService.monday(null));
    }
    @Test void isoWeekKeepsItsCorrectMondayAcrossYears(){
        assertEquals(LocalDate.of(2020,12,28),SupplierOverviewService.monday(LocalDate.of(2021,1,1)));
        assertEquals(LocalDate.of(2021,1,4),SupplierOverviewService.monday(LocalDate.of(2021,1,4)));
    }
    @Test void committedSupplyIsExactAndDoesNotModifyRemaining(){
        var row=new WeeklySupplyRecord("1","1","Beans",LocalDate.of(2026,11,9),new BigDecimal("10.25"),new BigDecimal("4.10"),BigDecimal.ONE,"kg");
        assertEquals(new BigDecimal("6.15"),row.committedQuantity());assertEquals(new BigDecimal("4.10"),row.remainingQuantity());
    }
}
