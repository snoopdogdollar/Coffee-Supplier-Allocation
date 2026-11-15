package com.coffeeallocation.model;

import java.time.*;
import java.time.temporal.IsoFields;

public final class DemandExpiryPolicy {
    public static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private DemandExpiryPolicy() {}
    public static boolean isPastWeek(int year,int week,LocalDate businessDate){
        if(year<1||year>9999||week<1||week>LocalDate.of(year,12,28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))throw new IllegalArgumentException("Choose a valid ISO delivery week.");
        int currentYear=businessDate.get(IsoFields.WEEK_BASED_YEAR),currentWeek=businessDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return year<currentYear||(year==currentYear&&week<currentWeek);
    }
    public static void requireActiveWeek(int year,int week,LocalDate businessDate){
        if(isPastWeek(year,week,businessDate))throw new IllegalArgumentException("The delivery week has expired. Use the current or a later week for a new demand.");
    }
    public static boolean isPastWeek(LocalDate deliveryDate,LocalDate businessDate){return isPastWeek(deliveryDate.get(IsoFields.WEEK_BASED_YEAR),deliveryDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),businessDate);}
}
