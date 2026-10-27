package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** A read-only snapshot of one demand item and its eligible supplies. */
public final class DemandSupplyMatch {

    private final OpenDemandItem demandItem;
    private final List<AvailableSupply> supplies;

    public DemandSupplyMatch(
            OpenDemandItem demandItem, List<AvailableSupply> supplies) {
        this.demandItem = Objects.requireNonNull(demandItem, "demandItem");
        this.supplies = List.copyOf(supplies);
    }

    public OpenDemandItem getDemandItem() {
        return demandItem;
    }

    public List<AvailableSupply> getSupplies() {
        return supplies;
    }

    public BigDecimal getTotalAvailable() {
        BigDecimal total = BigDecimal.ZERO;
        for (AvailableSupply supply : supplies) {
            total = total.add(supply.getRemainingQuantity());
        }
        return total;
    }
}
