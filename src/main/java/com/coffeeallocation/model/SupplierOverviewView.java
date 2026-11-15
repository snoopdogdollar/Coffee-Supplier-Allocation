package com.coffeeallocation.model;

import java.time.LocalDate;
import java.util.List;

public final class SupplierOverviewView {
    private SupplierOverviewView() {}
    public record Totals(long orders,long pending,long completed) {}
    public record Snapshot(SupplierSupplyView.Catalog catalog,LocalDate week,
                           List<SupplierSupplyView.Row> supply,Totals totals,List<OrderView.BuyerOrder> orders) {
        public Snapshot {supply=List.copyOf(supply);orders=List.copyOf(orders);}
    }
}
