package com.coffeeallocation.console;

import com.coffeeallocation.service.SupplierHistoryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public final class SupplierHistoryConsole {
    private SupplierHistoryConsole() {}

    public static void run(Scanner scanner) throws SQLException {
        System.out.println("=== Supplier review history ===");
        System.out.println("Cutoff format: YYYY-MM-DD HH:mm:ss (database local time).");
        while (true) {
            System.out.print("Cutoff (Enter for now, 0 to return): ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().trim();
            if (input.equals("0")) {
                return;
            }

            try {
                LocalDateTime cutoff = input.isEmpty()
                        ? null : LocalDateTime.parse(input.replace(' ', 'T'));
                var report = new SupplierHistoryService().findReport(cutoff);
                System.out.println("Review cutoff: "
                        + report.cutoff().timestamp().toString().replace('T', ' ')
                        + " | Time zone: " + report.cutoff().timeZone());
                System.out.println(
                        "Each COMPLETED order contributes its first eligible review."
                );
                System.out.println("Percentages reflect shop feedback.");
                if (report.suppliers().isEmpty()) {
                    System.out.println("No suppliers found.");
                }
                for (var summary : report.suppliers()) {
                    System.out.println();
                    System.out.println("Supplier " + summary.supplierId()
                            + " | " + summary.supplierName());
                    System.out.println("Reviewed orders: " + summary.reviewCount());
                    System.out.println("Quality OK: "
                            + percentage(summary.qualityOkCount(), summary.reviewCount()));
                    System.out.println("Quantity OK: "
                            + percentage(summary.quantityOkCount(), summary.reviewCount()));
                    System.out.println("On-time feedback: "
                            + percentage(summary.onTimeOkCount(), summary.reviewCount()));
                    if (summary.latestReviewAt() != null) {
                        System.out.println("Latest counted review: "
                                + summary.latestReviewAt().toString().replace('T', ' '));
                    }
                }
                return;
            } catch (DateTimeParseException exception) {
                System.out.println("Enter a valid date and time, for example 2026-10-09 10:00:00.");
            } catch (IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    private static String percentage(long positive, long total) {
        if (total == 0) {
            return "N/A (no reviews)";
        }
        String rate = BigDecimal.valueOf(positive)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .toPlainString();
        return positive + "/" + total + " (" + rate + "%)";
    }
}
