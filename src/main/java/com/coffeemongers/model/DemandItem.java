package com.coffeemongers.model;

import java.math.BigDecimal;

/** Presentation line; maps productName/unit from COFFEE_PRODUCTS and quantity/note to DEMAND_ITEMS. */
public record DemandItem(String productName, BigDecimal quantity, String unit, String note) {
    public DemandItem(String productName, BigDecimal quantity, String unit) { this(productName, quantity, unit, ""); }
    public DemandItem {
        if (productName == null || productName.isBlank()) throw new IllegalArgumentException("Product is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Unit is required");
        note = note == null ? "" : note;
    }

}
