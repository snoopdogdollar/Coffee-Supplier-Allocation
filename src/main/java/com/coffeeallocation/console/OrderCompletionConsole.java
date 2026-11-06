package com.coffeeallocation.console;

import com.coffeeallocation.service.OrderService;
import java.sql.SQLException;
import java.util.Scanner;

public final class OrderCompletionConsole {
    private OrderCompletionConsole() {}

    public static void run(Scanner scanner) throws SQLException {
        System.out.println("=== Complete a pending order ===");
        System.out.println("Use menu 4 for shop IDs and menu 9 for order IDs.");

        Long shopId = readId(scanner, "Shop ID");
        if (shopId == null) {
            return;
        }
        Long orderId = readId(scanner, "Order ID");
        if (orderId == null) {
            return;
        }

        System.out.print("Complete order " + orderId + " for shop " + shopId
                + " after delivery? (y/N): ");
        if (!scanner.hasNextLine()
                || !scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Completion cancelled.");
            return;
        }

        if (new OrderService().completePendingOrder(shopId, orderId)) {
            System.out.println("Order " + orderId + " is now COMPLETED.");
        } else {
            System.out.println("No pending order with that ID belongs to this shop.");
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
}
