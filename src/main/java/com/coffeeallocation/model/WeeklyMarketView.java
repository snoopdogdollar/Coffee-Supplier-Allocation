package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

public final class WeeklyMarketView {
    private WeeklyMarketView() {}
    public record Product(long id, String name, String unit, boolean active) {
        @Override public String toString() {return name+" · "+unit+" (#"+id+")"+(active?"":" · inactive");}
    }
    public record Week(LocalDate monday, BigDecimal supply, BigDecimal demand) {
        public String label() {return "%d-W%02d".formatted(monday.get(IsoFields.WEEK_BASED_YEAR),
                monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));}
    }
    public record Chart(List<Product> products, Product product, int endYear, int endWeek, List<Week> weeks) {
        public Chart {products=List.copyOf(products);weeks=List.copyOf(weeks);}
    }
}
