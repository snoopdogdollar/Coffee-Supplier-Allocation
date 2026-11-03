package com.coffeeallocation.console;

import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.service.AllocationConfirmationService;
import com.coffeeallocation.service.SharedAllocationService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Scanner;

public final class AllocationConfirmationConsole {
    private AllocationConfirmationConsole() {}

    public static void run(Scanner scanner, int year, int week) throws SQLException {
        // preview() finishes and closes its connection before user input.
        BatchAllocationPreview displayedPreview =
                new SharedAllocationService().preview(year, week);
        SharedAllocationConsole.printPreview(displayedPreview, year, week);

        int allocatableItems = 0;
        int proposedOrders = 0;
        BigDecimal totalCost = BigDecimal.ZERO;

        System.out.println();
        System.out.println("=== Prices for the proposed orders ===");
        for (var result : displayedPreview.getItems()) {
            var item = result.getItem();
            if (!result.getAllocations().isEmpty()) {
                allocatableItems++;
            }
            for (var entry : result.getAllocations().entrySet()) {
                var supply = displayedPreview.findSupply(
                        item.getProductId(), entry.getKey());
                BigDecimal lineCost = entry.getValue()
                        .multiply(supply.getUnitPrice())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                totalCost = totalCost.add(lineCost);
                proposedOrders++;
                System.out.println("Item " + item.getDemandItemId()
                        + " | " + supply.getSupplierName()
                        + " | " + entry.getValue().toPlainString() + " " + item.getUnit()
                        + " | Unit price: " + supply.getUnitPrice().toPlainString()
                        + " VND/" + item.getUnit()
                        + " | Total: " + lineCost.toPlainString() + " VND");
            }
        }

        if (allocatableItems == 0) {
            System.out.println("No items can be allocated. No orders were created.");
            return;
        }

        System.out.println("Items to confirm: " + allocatableItems);
        System.out.println("Orders to create: " + proposedOrders);
        System.out.println("Total proposed cost: " + totalCost.toPlainString() + " VND");
        System.out.println("This confirms ALL allocatable items shown above for this week.");
        System.out.println("Items with insufficient supply stay unallocated.");

        while (true) {
            System.out.print("Deduct supply and create PENDING orders? [y/N]: ");
            if (!scanner.hasNextLine()) {
                System.out.println("Confirmation cancelled. Nothing was saved.");
                return;
            }
            String answer = scanner.nextLine().trim();
            if (answer.isEmpty() || answer.equalsIgnoreCase("n")) {
                System.out.println("Confirmation cancelled. Nothing was saved.");
                return;
            }
            if (answer.equalsIgnoreCase("y")) {
                break;
            }
            System.out.println("Please enter y or n.");
        }

        try {
            // Pass the displayed object, not an unseen replacement preview.
            var result = new AllocationConfirmationService()
                    .confirm(year, week, displayedPreview);
            System.out.println("Confirmation committed.");
            System.out.println("Allocated items: " + result.allocatedItems());
            System.out.println("Items still unallocated: " + result.unallocatedItems());
            System.out.println("Created PENDING order IDs: " + result.orderIds());
            System.out.println("Demands now ALLOCATED: " + result.allocatedDemandIds());
        } catch (IllegalStateException exception) {
            System.out.println("Cannot confirm: " + exception.getMessage());
            System.out.println("Choose menu 8 again to review a fresh preview.");
        } catch (SQLException exception) {
            String state = exception.getSQLState();
            if ("40001".equals(state) || "40P01".equals(state)) {
                System.out.println("Concurrent changes prevented confirmation.");
                System.out.println("Choose menu 8 again to review a fresh preview.");
                return;
            }
            throw exception;
        }
    }
}
