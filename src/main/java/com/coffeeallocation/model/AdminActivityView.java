package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AdminActivityView {
    private AdminActivityView() { }
    public record Order(long id, long demandId, LocalDate date, long shopId, String shopName,
                        long supplierId, String supplierName, String status, int itemCount, BigDecimal total) { }
    public record Demand(long id, LocalDate requestDate, int year, int week, long shopId,
                         String shopName, String status, int itemCount, String note) { }
    public record DemandLine(long productId, String productName, String unit, BigDecimal quantity, String note) { }
    public record DemandDetails(Demand summary, List<DemandLine> items) {
        public DemandDetails { items = List.copyOf(items); }
    }
    public record Page<T>(List<T> rows, Long nextBeforeId) {
        public Page { rows = List.copyOf(rows); }
    }
    public record Snapshot(Page<Order> orders, Page<Demand> demands) { }
}
