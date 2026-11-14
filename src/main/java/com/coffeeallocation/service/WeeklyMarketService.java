package com.coffeeallocation.service;

import com.coffeeallocation.dao.WeeklyMarketDao;
import com.coffeeallocation.model.WeeklyMarketView.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public final class WeeklyMarketService {
    public static final int WINDOW_WEEKS=8;
    public static void validate(Long productId,Integer year,Integer week) {
        if(productId!=null&&productId<=0)throw new IllegalArgumentException("Choose a valid product.");
        if((year==null)!=(week==null))throw new IllegalArgumentException("Enter both the ending ISO year and week.");
        if(year==null)return;
        if(year<1||year>9999)throw new IllegalArgumentException("ISO year must be between 1 and 9999.");
        int lastWeek=LocalDate.of(year,12,28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if(week<1||week>lastWeek)throw new IllegalArgumentException("Choose a valid ISO week for this year (1–"+lastWeek+").");
    }
    public static LocalDate endingMonday(int year,int week) {
        validate(null,year,week);
        return LocalDate.of(year,1,4).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(week-1);
    }
    public static LocalDate startingMonday(LocalDate end) {
        LocalDate start=end.minusWeeks(WINDOW_WEEKS-1);
        return start.isBefore(LocalDate.of(1,1,1))?LocalDate.of(1,1,1):start;
    }
    /** Uses the caller's read-only transaction, keeping the dashboard on one snapshot. */
    public Chart load(Connection connection,LocalDate databaseDate,Long productId,Integer year,Integer week) throws SQLException {
        validate(productId,year,week);
        int endYear=year==null?databaseDate.get(IsoFields.WEEK_BASED_YEAR):year;
        int endWeek=week==null?databaseDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR):week;
        LocalDate end=endingMonday(endYear,endWeek);
        var dao=new WeeklyMarketDao(connection);var products=dao.products();
        Product selected=productId==null?(products.isEmpty()?null:products.getFirst()):
                products.stream().filter(p -> p.id()==productId).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("This product is no longer available. Reload the dashboard."));
        return new Chart(products,selected,endYear,endWeek,selected==null?List.of():dao.weeks(selected.id(),startingMonday(end),end));
    }
    public Chart demo(LocalDate today) {
        int year=today.get(IsoFields.WEEK_BASED_YEAR),week=today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        LocalDate end=endingMonday(year,week),start=startingMonday(end);
        var product=new Product(1,"Demo Arabica","kg",true);
        int[] supply={70,90,80,110,100,125,95,120},demand={60,85,95,90,105,110,100,130};
        List<Week> weeks=new ArrayList<>();int i=0;
        for(LocalDate date=start;!date.isAfter(end);date=date.plusWeeks(1),i++)
            weeks.add(new Week(date,BigDecimal.valueOf(supply[i]),BigDecimal.valueOf(demand[i])));
        return new Chart(List.of(product),product,year,week,weeks);
    }
}
