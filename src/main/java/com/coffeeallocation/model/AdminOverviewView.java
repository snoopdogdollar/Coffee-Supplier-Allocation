package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AdminOverviewView {
    private AdminOverviewView() { }
    public record Totals(long orders,long pendingOrders,long activeSuppliers) { }
    public record RecentOrder(long id,LocalDate date,long shopId,String shopName,long supplierId,
                              String supplierName,String status,int itemCount,BigDecimal total) { }
    public record Snapshot(Totals totals,List<RecentOrder> recentOrders) {
        public Snapshot {recentOrders=List.copyOf(recentOrders);}
    }
}
