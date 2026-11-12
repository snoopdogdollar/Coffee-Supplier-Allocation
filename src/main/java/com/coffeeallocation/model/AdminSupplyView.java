package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.util.List;

public final class AdminSupplyView {
    private AdminSupplyView() { }
    public record Row(long supplyId, long supplierProductId, long supplierId, String supplierName,
                      long productId, String productName, String unit, int year, int week,
                      BigDecimal initialQuantity, BigDecimal remainingQuantity, BigDecimal unitPrice,
                      boolean supplierActive, boolean productActive, boolean offeringActive) {
        public BigDecimal committedQuantity() { return initialQuantity.subtract(remainingQuantity); }
        public String eligibility() {
            if (!supplierActive) return "Supplier inactive";
            if (!productActive) return "Product inactive";
            if (!offeringActive) return "Offering inactive";
            return remainingQuantity.signum() > 0 ? "Available" : "No remaining quantity";
        }
    }
    public record Page(List<Row> rows, Long nextBeforeId) {
        public Page { rows = List.copyOf(rows); }
    }
}
