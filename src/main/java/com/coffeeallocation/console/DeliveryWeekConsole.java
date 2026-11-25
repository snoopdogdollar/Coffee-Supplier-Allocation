package com.coffeeallocation.console;

import com.coffeeallocation.model.DeliveryWeek;
import java.io.PrintStream;
import java.util.Scanner;

/** Collects a valid year/week without opening a database connection. */
public final class DeliveryWeekConsole {
    private DeliveryWeekConsole() {}

    public static DeliveryWeek select(Scanner scanner, PrintStream out, DeliveryWeek current) {
        out.println("Current selection: ISO week " + current.week() + "/" + current.year());
        out.println("Historical weeks may be viewed; new demands and allocation require a current/future week.");
        while (true) {
            out.print("ISO week-based year (1-9999; Enter cancels): ");
            if (!scanner.hasNextLine()) return current;
            String yearText = scanner.nextLine().trim();
            if (yearText.isEmpty()) return current;
            int year;
            try {
                year = Integer.parseInt(yearText);
                // Validate the year before asking for a week.
                new DeliveryWeek(year, 1);
            } catch (IllegalArgumentException exception) {
                out.println("Enter an ISO year between 1 and 9999.");
                continue;
            }

            out.print("ISO week (Enter cancels): ");
            if (!scanner.hasNextLine()) return current;
            String weekText = scanner.nextLine().trim();
            if (weekText.isEmpty()) return current;
            try {
                DeliveryWeek selected = new DeliveryWeek(year, Integer.parseInt(weekText));
                out.println("Selected delivery period: ISO week " + selected.week() + "/" + selected.year());
                return selected;
            } catch (NumberFormatException exception) {
                out.println("Enter a whole-number ISO week.");
            } catch (IllegalArgumentException exception) {
                out.println(exception.getMessage());
            }
        }
    }
}
