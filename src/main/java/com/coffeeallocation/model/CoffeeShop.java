package com.coffeeallocation.model;

public class CoffeeShop {

    private final long shopId;
    private final String name;

    public CoffeeShop(long shopId, String name) {
        this.shopId = shopId;
        this.name = name;
    }

    public long getShopId() {
        return shopId;
    }

    public String getName() {
        return name;
    }
}