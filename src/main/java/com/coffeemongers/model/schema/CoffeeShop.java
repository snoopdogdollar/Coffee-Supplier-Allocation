package com.coffeemongers.model.schema;
import java.time.LocalDateTime;
public record CoffeeShop(long shopId, String name, String contactPerson, String phone, String email, String address, boolean isActive, LocalDateTime createdAt) { }
