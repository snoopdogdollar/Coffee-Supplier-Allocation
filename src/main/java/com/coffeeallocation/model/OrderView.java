package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OrderView {
    private OrderView() {}

    public record Summary(
            long orderId,
            LocalDate orderDate,
            String shopName,
            String supplierName,
            long allocationId,
            String status,
            int itemCount,
            BigDecimal totalPrice) {}

    public record Line(
            long productId,
            String productName,
            String unit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice) {}

    public record Details(Summary summary, List<Line> items) {
        public Details {
            items = List.copyOf(items);
        }
    }

    public record BuyerSummary(long orderId, long demandId, long supplierId,
                               String supplierName, LocalDate orderDate,
                               String status, boolean reviewed, long shopId, String shopName) {
        public BuyerSummary(long orderId, long demandId, long supplierId, String supplierName,
                            LocalDate orderDate, String status, boolean reviewed) {
            this(orderId, demandId, supplierId, supplierName, orderDate, status, reviewed, 0, "");
        }
    }

    public record BuyerOrder(BuyerSummary summary, List<Line> items) {
        public BuyerOrder {
            items = List.copyOf(items);
        }
    }
}
