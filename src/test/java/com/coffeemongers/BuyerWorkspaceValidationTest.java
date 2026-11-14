package com.coffeemongers;

import com.coffeemongers.service.BuyerWorkspaceService;
import com.coffeemongers.model.*;
import com.coffeemongers.service.InMemoryMarketplaceService;
import com.coffeeallocation.model.BuyerWorkspaceView.*;
import com.coffeemongers.view.BuyerDemandRow;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuyerWorkspaceValidationTest {
    @Test void invalidShopAndIdentifiersFailBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> new BuyerWorkspaceService(0));
        var service=new BuyerWorkspaceService(1);
        assertThrows(IllegalArgumentException.class,() -> service.detail(0));
        assertThrows(IllegalArgumentException.class,() -> service.demands(-1L));
    }
    @Test void lineBalancesKeepDecimalPrecision() {
        var line=new Line(1,1,"Arabica","kg",new BigDecimal("10.25"),new BigDecimal("4.10"),"");
        assertEquals(new BigDecimal("6.15"),line.remaining());
    }
    @Test void displayKeepsMixedUnitsSeparateAndCountsAllocatedLines() {
        var demand=new Demand(1,LocalDate.of(2026,11,14),2026,46,"OPEN","",List.of(
                new Line(1,1,"Beans","kg",new BigDecimal("10"),new BigDecimal("10"),""),
                new Line(2,2,"Bags","bag",new BigDecimal("2"),BigDecimal.ZERO,"")));
        assertEquals("10 kg, 2 bag",BuyerDemandRow.of(demand).quantities());
        assertEquals("1 / 2 lines allocated",demand.progress());assertEquals("2026-W46",demand.deliveryWeek());
    }
    @Test void demoSubmitPreservesDeliveryWeekWithoutCreatingOrders() {
        var service=new InMemoryMarketplaceService();
        var draft=new DemandRequest("","Weekly coffee",LocalDate.of(2026,12,20),"Demo shop","",List.of(
                new DemandItem(service.coffeeProducts().getFirst(),BigDecimal.TEN,"kg")),LocalDate.of(2027,1,4));
        var saved=service.submitDemand(draft);
        assertEquals("2027-W01",BuyerDemandRow.demo(saved).week());assertEquals(draft.requestDate(),saved.requestDate());
        assertTrue(service.buyerOrders().isEmpty());
    }
}
