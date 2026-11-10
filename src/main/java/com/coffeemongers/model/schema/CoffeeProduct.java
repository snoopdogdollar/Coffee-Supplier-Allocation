package com.coffeemongers.model.schema;
import java.time.LocalDateTime;
public record CoffeeProduct(long productId, String name, String description, String unit, String variety, boolean isActive, LocalDateTime createdAt) { }
