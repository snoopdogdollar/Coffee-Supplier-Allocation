package com.coffeemongers.service;

import com.coffeeallocation.service.OrderService;
import com.coffeeallocation.service.SupplierReviewService;
import com.coffeemongers.model.OrderItem;
import com.coffeemongers.model.OrderRecord;

import java.sql.SQLException;
import java.util.List;

/** Order operations bound to the configured Buyer's coffee shop. */
public final class BuyerOrderService {
    private static final int PAGE_SIZE = 20;
    private final long shopId;
    private final OrderService orders = new OrderService();
    private final SupplierReviewService reviews = new SupplierReviewService();

    public record Page(List<OrderRecord> orders, Long nextBeforeId) {
        public Page {
            orders = List.copyOf(orders);
        }
    }

    public BuyerOrderService(long shopId) {
        if (shopId <= 0) {
            throw new IllegalArgumentException("Shop ID must be positive.");
        }
        this.shopId = shopId;
    }

    public Page page(Long beforeId) throws SQLException {
        var rows = orders.findBuyerPage(shopId, beforeId);
        var visible = rows.subList(0, Math.min(rows.size(), PAGE_SIZE));
        List<OrderRecord> records = visible.stream().map(row -> {
            var header = row.summary();
            List<OrderItem> items = row.items().stream().map(item ->
                    new OrderItem(item.productName(), item.quantity(), item.unit(),
                            item.unitPrice(), item.totalPrice())).toList();
            return new OrderRecord(Long.toString(header.orderId()), Long.toString(header.demandId()),
                    Long.toString(header.supplierId()), header.supplierName(), header.orderDate(),
                    header.status(), items, header.reviewed());
        }).toList();
        Long next = rows.size() > PAGE_SIZE ? visible.getLast().summary().orderId() : null;
        return new Page(records, next);
    }

    public void complete(long orderId) throws SQLException {
        if (!orders.completePendingOrder(shopId, orderId)) {
            throw new IllegalArgumentException("This shop has no PENDING order with that ID. Refresh orders.");
        }
    }

    public long review(long orderId, boolean qualityOk, boolean quantityOk, boolean onTimeOk,
                       String comment) throws SQLException {
        return reviews.submit(shopId, orderId, qualityOk, quantityOk, onTimeOk, comment);
    }
}
