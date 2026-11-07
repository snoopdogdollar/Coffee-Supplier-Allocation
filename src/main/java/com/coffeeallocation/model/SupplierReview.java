package com.coffeeallocation.model;

import java.time.LocalDateTime;

public record SupplierReview(
        long reviewId,
        long orderId,
        long supplierId,
        boolean qualityOk,
        boolean quantityOk,
        boolean onTimeOk,
        String comment,
        LocalDateTime createdAt) {}
