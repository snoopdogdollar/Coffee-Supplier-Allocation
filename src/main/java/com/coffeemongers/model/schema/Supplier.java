package com.coffeemongers.model.schema;
import java.time.LocalDateTime;
public record Supplier(long supplierId, String name, String contactPerson, String phone, String email, String address, boolean isActive, LocalDateTime createdAt) { }
