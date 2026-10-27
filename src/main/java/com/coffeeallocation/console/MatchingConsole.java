package com.coffeeallocation.console;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.DemandSupplyMatch;
import com.coffeeallocation.model.OpenDemandItem;
import com.coffeeallocation.service.MatchingService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public final class MatchingConsole {

    private MatchingConsole() {
    }

    public static void run(Scanner scanner, int year, int week) throws SQLException {
        List<OpenDemandItem> items;
        try (Connection connection = DatabaseConnection.getConnection()) {
            items = new DemandDao(connection).findOpenItemsForWeek(year, week);
        }

        if (items.isEmpty()) {
            System.out.println("No OPEN demand items for this week.");
            return;
        }

        System.out.println("OPEN demand items for ISO week " + week + "/" + year + ":");
        for (OpenDemandItem item : items) {
            System.out.println(
                    "Item " + item.getDemandItemId()
                    + " | Demand " + item.getDemandId()
                    + " | " + item.getShopName()
                    + " | " + item.getProductName()
                    + " | Requested: " + item.getQuantity() + " " + item.getUnit());
        }

        // The listing connection is already closed while we wait for input.
        while (true) {
            System.out.print("Demand item ID (Enter to return): ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return;
            }

            long itemId;
            try {
                itemId = Long.parseLong(input);
            } catch (NumberFormatException exception) {
                System.out.println("Enter a numeric demand item ID from the list.");
                continue;
            }

            boolean listed = false;
            for (OpenDemandItem item : items) {
                if (item.getDemandItemId() == itemId) {
                    listed = true;
                    break;
                }
            }
            if (!listed) {
                System.out.println("Choose a demand item ID from the list.");
                continue;
            }

            try {
                DemandSupplyMatch match = new MatchingService()
                        .findForItem(itemId, year, week);
                printMatch(match);
            } catch (IllegalArgumentException exception) {
                System.out.println("Matching unavailable: " + exception.getMessage());
            }
            return;
        }
    }

    private static void printMatch(DemandSupplyMatch match) {
        OpenDemandItem item = match.getDemandItem();
        System.out.println("Eligible supply for " + item.getProductName()
                + " | " + item.getShopName());
        System.out.println("Original requested quantity: "
                + item.getQuantity() + " " + item.getUnit());

        for (AvailableSupply supply : match.getSupplies()) {
            System.out.println(
                    supply.getSupplierName()
                    + " | Available: " + supply.getRemainingQuantity() + " " + item.getUnit()
                    + " | " + supply.getUnitPrice() + " VND/" + item.getUnit());
        }
        if (match.getSupplies().isEmpty()) {
            System.out.println("No eligible supply found for this product and week.");
        }
        System.out.println("Total eligible supply: "
                + match.getTotalAvailable() + " " + item.getUnit());
        System.out.println("Supply is shared with other demands. This preview reserves nothing.");
    }
}
