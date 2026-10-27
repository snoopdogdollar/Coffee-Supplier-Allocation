package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NewDemandItem {

    private final long productId;
    private final BigDecimal quantity;
    private final String note;

    public NewDemandItem(long productId, BigDecimal quantity, String note) {
        if (productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive.");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        BigDecimal normalizedQuantity;
        try {
            normalizedQuantity = quantity.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                "Quantity must be representable with two decimal places.", exception
            );
        }
        if (normalizedQuantity.precision() > 15) {
            throw new IllegalArgumentException("Quantity exceeds DECIMAL(15,2).");
        }
        if (note != null && note.codePointCount(0, note.length()) > 255) {
            throw new IllegalArgumentException("Item note must be at most 255 characters.");
        }

        this.productId = productId;
        this.quantity = normalizedQuantity;
        this.note = note;
    }

    public long getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getNote() {
        return note;
    }
}
