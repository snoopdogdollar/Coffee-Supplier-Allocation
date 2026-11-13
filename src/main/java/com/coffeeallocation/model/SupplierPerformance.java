package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.util.List;

/** Current supplier feedback; scores and rates are absent until a review exists. */
public final class SupplierPerformance {
    private SupplierPerformance() {}

    public record Entry(SupplierHistory.Summary history, BigDecimal qualityRate,
                        BigDecimal quantityRate, BigDecimal onTimeRate, BigDecimal score,
                        boolean topEligible) {}

    public record Rankings(List<Entry> suppliers, List<Entry> topSuppliers) {
        public Rankings {
            suppliers = List.copyOf(suppliers);
            topSuppliers = List.copyOf(topSuppliers);
        }
    }
}
