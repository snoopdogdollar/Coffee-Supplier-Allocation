package com.coffeeallocation.model;

public class CoffeeProduct {

    private final long productId;
    private final String name;
    private final String unit;

    public CoffeeProduct(long productId, String name, String unit) {
        this.productId = productId;
        this.name = name;
        this.unit = unit;
    }

    public long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }
}