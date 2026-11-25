package com.coffeeallocation.model;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/** An ISO delivery week, including its week-based year. Historical weeks are valid. */
public record DeliveryWeek(int year, int week) {
    public DeliveryWeek {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("ISO year must be between 1 and 9999.");
        }
        int lastWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) {
            throw new IllegalArgumentException("Choose an ISO week from 1 to " + lastWeek
                    + " for year " + year + ".");
        }
    }

    public static DeliveryWeek from(LocalDate date) {
        return new DeliveryWeek(date.get(IsoFields.WEEK_BASED_YEAR),
                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
