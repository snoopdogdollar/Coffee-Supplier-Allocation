package com.coffeemongers.model.schema;
import java.time.LocalDateTime;
public record SupplierReviewRow(long reviewId, long orderId, long supplierId, boolean qualityOk, boolean quantityOk, boolean onTimeOk, String comment, LocalDateTime createdAt) { }
