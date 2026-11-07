package com.coffeeallocation.console;

import com.coffeeallocation.service.SupplierReviewService;
import java.sql.SQLException;
import java.util.Scanner;

public final class SupplierReviewConsole {
    private SupplierReviewConsole() {}

    public static void run(Scanner scanner) throws SQLException {
        System.out.println("=== Supplier review for an order ===");
        System.out.println("Use menu 4 for shop IDs and menu 9 for order IDs.");
        System.out.println("Each COMPLETED order can be reviewed once.");

        Long shopId = readId(scanner, "Shop ID");
        if (shopId == null) {
            return;
        }
        Long orderId = readId(scanner, "Order ID");
        if (orderId == null) {
            return;
        }

        SupplierReviewService service = new SupplierReviewService();
        var reviews = service.findByOrder(shopId, orderId);
        if (!reviews.isEmpty()) {
            for (var review : reviews) {
                System.out.println("Review " + review.reviewId()
                        + " | Supplier " + review.supplierId()
                        + " | Quality OK: " + review.qualityOk()
                        + " | Quantity OK: " + review.quantityOk()
                        + " | On time: " + review.onTimeOk()
                        + " | " + review.createdAt());
                if (review.comment() != null) {
                    System.out.println(review.comment());
                }
            }
            System.out.println("This order already has a review.");
            return;
        }

        Boolean qualityOk = readAnswer(scanner, "Was the quality satisfactory?");
        if (qualityOk == null) {
            return;
        }
        Boolean quantityOk = readAnswer(scanner, "Was the quantity correct?");
        if (quantityOk == null) {
            return;
        }
        Boolean onTimeOk = readAnswer(scanner, "Was delivery on time?");
        if (onTimeOk == null) {
            return;
        }

        System.out.print("Comment (optional, up to 255 characters): ");
        if (!scanner.hasNextLine()) {
            return;
        }
        String comment = scanner.nextLine();
        System.out.print("Save review? (y/N): ");
        if (!scanner.hasNextLine()
                || !scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Review cancelled.");
            return;
        }

        try {
            long reviewId = service.submit(
                    shopId, orderId, qualityOk, quantityOk, onTimeOk, comment
            );
            System.out.println("Review saved. ID: " + reviewId);
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        }
    }

    private static Long readId(Scanner scanner, String label) {
        while (true) {
            System.out.print(label + " (Enter to return): ");
            if (!scanner.hasNextLine()) {
                return null;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return null;
            }
            try {
                long id = Long.parseLong(input);
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // Invalid input can be retried.
            }
            System.out.println("Enter a positive " + label + ".");
        }
    }

    private static Boolean readAnswer(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n, Enter to cancel): ");
            if (!scanner.hasNextLine()) {
                return null;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return null;
            }
            if (input.equalsIgnoreCase("y")) {
                return true;
            }
            if (input.equalsIgnoreCase("n")) {
                return false;
            }
            System.out.println("Enter y or n.");
        }
    }
}
