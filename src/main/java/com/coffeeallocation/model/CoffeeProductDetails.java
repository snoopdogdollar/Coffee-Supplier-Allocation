package com.coffeeallocation.model;

import java.time.LocalDateTime;

public record CoffeeProductDetails(long productId, String name, String description,
                                  String unit, String variety, boolean active,
                                  LocalDateTime createdAt) { }
