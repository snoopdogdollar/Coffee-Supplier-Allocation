package com.coffeemongers.model;

import java.math.BigDecimal;

public record OrderItem(String productName, BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal totalPrice) {
    public OrderItem(String productName, BigDecimal quantity, BigDecimal unitPrice, String unit) { this(productName, quantity, unit, unitPrice, quantity.multiply(unitPrice)); }
    public OrderItem(String productName, BigDecimal quantity, BigDecimal unitPrice) { this(productName, quantity, "kg", unitPrice, quantity.multiply(unitPrice)); }
    public OrderItem { unit = unit == null || unit.isBlank() ? "kg" : unit; }
}
