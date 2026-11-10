package com.coffeemongers.model;

import java.math.BigDecimal;

/** Presentation DTO assembled from SUPPLIER_SCORES, SUPPLIER_WEEKLY_SUPPLY and related rows. */
public record SupplierMatch(String id, String supplierId, String supplierName, String supplierLocation,
                            String productName, BigDecimal availableQuantity, BigDecimal unitPrice,
                            BigDecimal score, String unit) {
    public SupplierMatch(String id, String supplierId, String supplierName, String supplierLocation,
                         String productName, BigDecimal availableQuantity, BigDecimal unitPrice, BigDecimal score) {
        this(id, supplierId, supplierName, supplierLocation, productName, availableQuantity, unitPrice, score, "kg");
    }
    public SupplierMatch { unit = unit == null || unit.isBlank() ? "kg" : unit; }
    public String location() { return supplierLocation; }
}
