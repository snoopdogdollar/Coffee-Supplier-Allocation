package com.coffeeallocation.model;

import java.util.List;

public final class AdminSupplierProductView {
    private AdminSupplierProductView() { }
    public record Row(long offeringId, long supplierId, String supplierName, long productId,
                      String productName, String unit, boolean offeringActive, boolean supplierActive,
                      boolean productActive, boolean hasWeeklySupply) { }
    public record Supplier(long id, String name) { }
    public record Page(List<Row> rows, Long nextBeforeId) {
        public Page { rows = List.copyOf(rows); }
    }
    public record Snapshot(Page page, List<Supplier> suppliers, List<CoffeeProduct> products) {
        public Snapshot { suppliers = List.copyOf(suppliers); products = List.copyOf(products); }
    }
}
