package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;

public final class SupplyComposition {
    private SupplyComposition() {}
    public enum Measure {
        REMAINING("Remaining supply"),DECLARED("Declared supply");
        private final String label;Measure(String label){this.label=label;}
        @Override public String toString(){return label;}
    }
    public record Product(long id,String name,String unit) {}
    public record Contribution(long productId,long supplierId,String supplierName,BigDecimal declared,BigDecimal remaining) {
        public BigDecimal amount(Measure measure){return measure==Measure.REMAINING?remaining:declared;}
    }
    public record Snapshot(LocalDate week,LocalDateTime loadedAt,List<Product> products,List<Contribution> contributions) {
        public Snapshot {products=List.copyOf(products);contributions=List.copyOf(contributions);}
        public BigDecimal total(long productId,Measure measure){return contributions.stream().filter(c -> c.productId()==productId).map(c -> c.amount(measure)).reduce(BigDecimal.ZERO,BigDecimal::add);}
        public BigDecimal amount(long productId,long supplierId,Measure measure){return contributions.stream().filter(c -> c.productId()==productId&&c.supplierId()==supplierId).map(c -> c.amount(measure)).reduce(BigDecimal.ZERO,BigDecimal::add);}
        public BigDecimal percentage(long productId,long supplierId,Measure measure){var total=total(productId,measure);return total.signum()==0?BigDecimal.ZERO:amount(productId,supplierId,measure).multiply(BigDecimal.valueOf(100)).divide(total,1,RoundingMode.HALF_UP);}
        public List<String> units(){return products.stream().map(Product::unit).distinct().sorted(Comparator.<String>comparingLong(unit -> products.stream().filter(p -> p.unit().equals(unit)).count()).reversed().thenComparing(Comparator.naturalOrder())).toList();}
    }
}
