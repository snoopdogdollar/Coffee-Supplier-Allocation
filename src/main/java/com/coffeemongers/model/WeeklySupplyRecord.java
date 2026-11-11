package com.coffeemongers.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** UI row assembled from SUPPLIER_WEEKLY_SUPPLY and its supplier-product/product joins. */
public record WeeklySupplyRecord(String id, String supplierId, String productName, LocalDate weekStart,
                                 BigDecimal initialQuantity, BigDecimal remainingQuantity,
                                 BigDecimal unitPrice, String unit, long supplierProductId) {
    public WeeklySupplyRecord(String id, String supplierId, String productName, LocalDate weekStart,
                              BigDecimal initialQuantity, BigDecimal remainingQuantity,
                              BigDecimal unitPrice, String unit) {
        this(id, supplierId, productName, weekStart, initialQuantity, remainingQuantity, unitPrice, unit, 0);
    }
    public WeeklySupplyRecord(String id, String supplierId, String productName, LocalDate weekStart,
                              BigDecimal initialQuantity, BigDecimal remainingQuantity, BigDecimal unitPrice) {
        this(id, supplierId, productName, weekStart, initialQuantity, remainingQuantity, unitPrice, "kg");
    }
    public WeeklySupplyRecord { unit = unit == null || unit.isBlank() ? "kg" : unit; }
    public int week() { return weekStart.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()); }
    public int year() { return weekStart.get(java.time.temporal.WeekFields.ISO.weekBasedYear()); }
}
