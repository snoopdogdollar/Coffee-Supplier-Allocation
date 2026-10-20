package com.coffeeallocation.model;

import java.math.BigDecimal;

public class OpenDemandItem {

    private final long demandItemId;
    private final long demandId;
    private final long shopId;
    private final String shopName;
    private final long productId;
    private final String productName;
    private final BigDecimal quantity;
    private final String unit;

    public OpenDemandItem(
            long demandItemId,
            long demandId,
            long shopId,
            String shopName,
            long productId,
            String productName,
            BigDecimal quantity,
            String unit) {

        this.demandItemId = demandItemId;
        this.demandId = demandId;
        this.shopId = shopId;
        this.shopName = shopName;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unit = unit;
    }

    public long getDemandItemId() {
        return demandItemId;
    }

    public long getDemandId() {
        return demandId;
    }

    public long getShopId() {
        return shopId;
    }

    public String getShopName() {
        return shopName;
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }
}