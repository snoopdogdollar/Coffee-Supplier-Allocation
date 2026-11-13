package com.coffeeallocation.model;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminReviewView {
    private AdminReviewView() { }
    public record Row(long reviewId, long orderId, long shopId, String shopName, long supplierId,
                      String supplierName, String orderStatus, boolean qualityOk, boolean quantityOk,
                      boolean onTimeOk, String comment, LocalDateTime createdAt) { }
    public record Page(List<Row> rows, Long nextBeforeId) {
        public Page { rows=List.copyOf(rows); }
    }
}
