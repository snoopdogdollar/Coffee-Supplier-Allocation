package com.coffeeallocation.console;

import com.coffeeallocation.model.OrderView;
import com.coffeeallocation.service.OrderService;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public final class OrderConsole {
    private OrderConsole() {}

    public static void run(Scanner scanner) throws SQLException {
        OrderService service = new OrderService();
        List<OrderView.Summary> orders = service.findPage(null);

        while (true) {
            printOrders(orders);

            System.out.println(
                    "Order ID: view details | n: older | r: newest"
            );
            System.out.print("Choose (Enter to return): ");

            if (!scanner.hasNextLine()) {
                return;
            }

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return;
            }

            if (input.equalsIgnoreCase("r")) {
                orders = service.findPage(null);
                continue;
            }

            if (input.equalsIgnoreCase("n")) {
                if (orders.isEmpty()) {
                    System.out.println("No older orders.");
                    continue;
                }

                long lastId = orders.get(orders.size() - 1).orderId();
                var nextPage = service.findPage(lastId);

                if (nextPage.isEmpty()) {
                    System.out.println("No older orders.");
                } else {
                    orders = nextPage;
                }

                continue;
            }

            long orderId;

            try {
                orderId = Long.parseLong(input);

                if (orderId <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                System.out.println(
                        "Enter a positive Order ID, n, r, or Enter."
                );
                continue;
            }

            var details = service.findDetails(orderId);

            if (details.isEmpty()) {
                System.out.println("Order not found: " + orderId);
            } else {
                printDetails(details.get());
            }
        }
    }

    private static void printOrders(
            List<OrderView.Summary> orders) {

        System.out.println();
        System.out.println("=== Orders: newest IDs first ===");
        System.out.println("All weeks and statuses; 20 per page.");

        if (orders.isEmpty()) {
            System.out.println("No orders found.");
        }

        for (var order : orders) {
            System.out.println(
                    "Order " + order.orderId()
                    + " | " + order.orderDate()
                    + " | " + order.shopName()
                    + " -> " + order.supplierName()
                    + " | " + order.status()
                    + " | Lines: " + order.itemCount()
                    + " | " + order.totalPrice().toPlainString()
                    + " VND"
            );
        }
    }

    private static void printDetails(OrderView.Details details) {
        var order = details.summary();

        System.out.println();
        System.out.println("=== Order " + order.orderId() + " ===");
        System.out.println("Date: " + order.orderDate());
        System.out.println("Shop: " + order.shopName());
        System.out.println("Supplier: " + order.supplierName());
        System.out.println("Status: " + order.status());
        System.out.println("Allocation ID: " + order.allocationId());

        for (var item : details.items()) {
            System.out.println(
                    "Product " + item.productId()
                    + " | " + item.productName()
                    + " | " + item.quantity().toPlainString()
                    + " " + item.unit()
                    + " | Unit price: "
                    + item.unitPrice().toPlainString()
                    + " VND/" + item.unit()
                    + " | Total: "
                    + item.totalPrice().toPlainString()
                    + " VND"
            );
        }

        System.out.println(
                "Order total: "
                + order.totalPrice().toPlainString()
                + " VND"
        );
    }
}