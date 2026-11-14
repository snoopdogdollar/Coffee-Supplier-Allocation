package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BuyerWorkspaceView {
    private BuyerWorkspaceView() {}
    public record Shop(long id,String name,String address) {}
    public record Counts(long demands,long openDemands,long orders,long pendingOrders,long reviewsDue) {}
    public record Line(long id,long productId,String productName,String unit,BigDecimal requested,
                       BigDecimal allocated,String note) {
        public BigDecimal remaining() {return requested.subtract(allocated).max(BigDecimal.ZERO);}
    }
    public record Demand(long id,LocalDate requestDate,int year,int week,String status,String note,List<Line> items) {
        public Demand {items=List.copyOf(items);}
        public String deliveryWeek() {return "%04d-W%02d".formatted(year,week);}
        public String products() {return items.stream().map(Line::productName).distinct().reduce((a,b)->a+", "+b).orElse("—");}
        public String quantities() {return items.stream().map(i -> i.requested().stripTrailingZeros().toPlainString()+" "+i.unit()).reduce((a,b)->a+", "+b).orElse("—");}
        public String progress() {return items.stream().filter(i -> i.remaining().signum()==0).count()+" / "+items.size()+" lines allocated";}
    }
    public record Allocation(long id,long itemId,String product,String unit,long supplierId,String supplier,
                             BigDecimal quantity,BigDecimal unitPrice) {}
    public record Detail(Shop shop,Demand demand,List<Allocation> allocations) {
        public Detail {allocations=List.copyOf(allocations);}
    }
    public record Page(List<Demand> demands,Long nextBeforeId) {
        public Page {demands=List.copyOf(demands);}
    }
}
