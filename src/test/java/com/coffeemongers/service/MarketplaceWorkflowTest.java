package com.coffeemongers.service;

import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeemongers.model.SupplierReview;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceWorkflowTest {
    @Test
    void buyerConfirmationCreatesOrdersConsumesSupplyAndAcceptsReview() {
        MarketplaceService service = new InMemoryMarketplaceService();
        String product = "Arabica - Washed";
        DemandRequest demand = service.submitDemand(new DemandRequest("", "Cafe restock",
                LocalDate.now().plusDays(8), "Quận 3", "", List.of(new DemandItem(product, BigDecimal.valueOf(200), "kg"))));
        var match = service.recommendations(demand.id()).stream()
                .filter(candidate -> candidate.supplierId().equals("SUP-001"))
                .findFirst().orElseThrow();
        var before = service.weeklySupply(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .stream().filter(row -> row.productName().equals(product)).findFirst().orElseThrow();

        var orders = service.confirmAllocations(demand.id(), Map.of(match.id(), BigDecimal.valueOf(200)));
        assertEquals(1, orders.size());
        assertEquals(demand.id(), orders.getFirst().demandId());
        var after = service.weeklySupply(before.weekStart()).stream()
                .filter(row -> row.productName().equals(product)).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(200), before.remainingQuantity().subtract(after.remainingQuantity()));

        service.markOrderDelivered(orders.getFirst().id());
        service.submitReview(new SupplierReview(orders.getFirst().supplierId(), orders.getFirst().id(), true,
                true, true, "Delivered as agreed", LocalDate.now()));
        assertTrue(service.buyerOrders().getFirst().reviewed());
    }

    @Test
    void allocationMustCoverEachRequestedProductBeforeOrdersAreCreated() {
        MarketplaceService service = new InMemoryMarketplaceService();
        DemandRequest demand = service.submitDemand(new DemandRequest("", "Cafe restock",
                LocalDate.now().plusDays(8), "Quận 3", "", List.of(new DemandItem("Arabica - Washed", BigDecimal.valueOf(200), "kg"))));
        var match = service.recommendations(demand.id()).getFirst();
        assertThrows(IllegalArgumentException.class,
                () -> service.confirmAllocations(demand.id(), Map.of(match.id(), BigDecimal.valueOf(100))));
        assertTrue(service.buyerOrders().isEmpty());
    }

    @Test
    void eachConfirmedSupplierAllocationCreatesItsOwnOrder() {
        MarketplaceService service = new InMemoryMarketplaceService();
        String product = "Robusta - Natural";
        DemandRequest demand = service.submitDemand(new DemandRequest("", "Robusta restock",
                LocalDate.now().plusDays(3), "Shop address", "", List.of(new DemandItem(product, BigDecimal.valueOf(100), "kg"))));
        var matches = service.recommendations(demand.id());
        var first = matches.stream().filter(m -> m.supplierId().equals("SUP-002")).findFirst().orElseThrow();
        var second = matches.stream().filter(m -> m.supplierId().equals("SUP-003")).findFirst().orElseThrow();

        var orders = service.confirmAllocations(demand.id(), Map.of(first.id(), BigDecimal.valueOf(60), second.id(), BigDecimal.valueOf(40)));

        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(order -> order.items().size() == 1));
        assertEquals(BigDecimal.valueOf(100), orders.stream().flatMap(order -> order.items().stream())
                .map(item -> item.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
