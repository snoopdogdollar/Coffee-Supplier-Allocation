package com.coffeemongers.model.schema;
import java.time.LocalDate; import java.time.LocalDateTime;
public record Order(long orderId, long shopId, long supplierId, long allocationId, LocalDate orderDate, String status, String note, LocalDateTime createdAt) { }
