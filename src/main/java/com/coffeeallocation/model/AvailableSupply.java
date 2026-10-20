package com.coffeeallocation.model;

import java.math.BigDecimal;

public class AvailableSupply {

    private final long supplyId;
    private final long supplierId;
    private final String supplierName;
    private final BigDecimal remainingQuantity;
    private final BigDecimal unitPrice;

    public AvailableSupply(
            long supplyId,
            long supplierId,
            String supplierName,
            BigDecimal remainingQuantity,
            BigDecimal unitPrice) {

        this.supplyId = supplyId;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.remainingQuantity = remainingQuantity;
        this.unitPrice = unitPrice;
    }

    public long getSupplyId() {
        return supplyId;
    }

    public long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}