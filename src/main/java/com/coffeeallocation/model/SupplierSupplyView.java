package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.util.List;

public final class SupplierSupplyView {
    private SupplierSupplyView() {}

    public record Product(long supplierProductId, long productId, String name, String unit) {}

    public record Catalog(String supplierName, List<Product> products) {
        public Catalog {
            products = List.copyOf(products);
        }
    }

    public record Row(long supplyId, long supplierProductId, String productName, String unit,
                      int year, int week, BigDecimal initialQuantity,
                      BigDecimal remainingQuantity, BigDecimal unitPrice) {}

    public record Snapshot(Catalog catalog, List<Row> rows) {
        public Snapshot {
            rows = List.copyOf(rows);
        }
    }
}
