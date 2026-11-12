package com.coffeemongers.service;

import com.coffeeallocation.service.OrderService;
import com.coffeemongers.model.OrderItem;
import com.coffeemongers.model.OrderRecord;
import java.sql.SQLException;
import java.util.List;

/** Incoming orders scoped to the configured supplier. */
public final class SupplierOrderService {
    private static final int PAGE_SIZE = 20;
    private final long supplierId;
    private final OrderService orders = new OrderService();

    public record IncomingOrder(OrderRecord order, long shopId, String shopName) {}

    public record Page(List<IncomingOrder> orders, Long nextBeforeId) {
        public Page {
            orders = List.copyOf(orders);
        }
    }

    public SupplierOrderService(long supplierId) {
        if (supplierId <= 0) throw new IllegalArgumentException("Supplier ID must be positive.");
        this.supplierId = supplierId;
    }

    public Page page(Long beforeId) throws SQLException {
        var rows = orders.findSupplierPage(supplierId, beforeId);
        var visible = rows.subList(0, Math.min(rows.size(), PAGE_SIZE));
        List<IncomingOrder> records = visible.stream().map(row -> {
            var header = row.summary();
            List<OrderItem> items = row.items().stream().map(item ->
                    new OrderItem(item.productName(), item.quantity(), item.unit(),
                            item.unitPrice(), item.totalPrice())).toList();
            OrderRecord order = new OrderRecord(Long.toString(header.orderId()), Long.toString(header.demandId()),
                    Long.toString(header.supplierId()), header.supplierName(), header.orderDate(),
                    header.status(), items, header.reviewed());
            return new IncomingOrder(order, header.shopId(), header.shopName());
        }).toList();
        Long next = rows.size() > PAGE_SIZE ? visible.getLast().summary().orderId() : null;
        return new Page(records, next);
    }
}
