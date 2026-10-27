package com.coffeeallocation.console;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.CoffeeShopDao;
import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.CoffeeShop;
import com.coffeeallocation.model.NewDemandItem;
import com.coffeeallocation.service.DemandService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class CreateDemandConsole {

    public static void run(Scanner scanner, int year, int week) throws SQLException {
        // Read the catalog, then close the connection before waiting for input.
        List<CoffeeShop> shops;
        List<CoffeeProduct> products;
        try (Connection connection = DatabaseConnection.getConnection()) {
            shops = new CoffeeShopDao(connection).findActive();
            products = new CoffeeProductDao(connection).findActive();
        }
        if (shops.isEmpty() || products.isEmpty()) {
            System.out.println("An active shop and an active product are required.");
            return;
        }

        Map<Long, CoffeeShop> shopsById = new LinkedHashMap<>();
        Map<Long, CoffeeProduct> productsById = new LinkedHashMap<>();
        System.out.println("=== Create demo OPEN demand ===");
        System.out.println("Required ISO week: " + week + "/" + year);
        for (CoffeeShop shop : shops) {
            shopsById.put(shop.getShopId(), shop);
            System.out.println(shop.getShopId() + " | " + shop.getName());
        }

        Long shopId = readId(scanner, "Shop", shopsById);
        if (shopId == null) {
            cancel();
            return;
        }
        CoffeeShop shop = shopsById.get(shopId);
        for (CoffeeProduct product : products) {
            productsById.put(product.getProductId(), product);
            System.out.println(product.getProductId() + " | " + product.getName()
                + " | " + product.getUnit());
        }

        List<NewDemandItem> items = new ArrayList<>();
        while (true) {
            Long productId = readId(scanner, "Product", productsById);
            if (productId == null) {
                cancel();
                return;
            }
            CoffeeProduct product = productsById.get(productId);
            NewDemandItem item = readItem(scanner, product);
            if (item == null) {
                cancel();
                return;
            }
            items.add(item);
            if (!readYes(scanner, "Add another item? [y/N]: ")) {
                break;
            }
        }

        LocalDate requestDate = LocalDate.now(ZoneOffset.UTC);
        System.out.println();
        System.out.println("Review demand for " + shop.getName());
        System.out.println("Request date (UTC): " + requestDate);
        System.out.println("Required ISO week: " + week + "/" + year);
        for (NewDemandItem item : items) {
            CoffeeProduct product = productsById.get(item.getProductId());
            System.out.println(product.getName() + " | "
                + item.getQuantity().toPlainString() + " " + product.getUnit());
        }
        System.out.println("Total items: " + items.size());
        if (!readYes(scanner, "Save this demand? [y/N]: ")) {
            cancel();
            return;
        }

        // The Service rechecks active IDs and owns the entire transaction.
        try {
            long demandId = new DemandService().createOpenDemand(
                shopId, requestDate, year, week, null, items
            );
            System.out.println("Demand saved. ID: " + demandId);
        } catch (IllegalArgumentException exception) {
            System.out.println("Cannot create demand: " + exception.getMessage());
        }
    }

    private static Long readId(Scanner scanner, String label, Map<Long, ?> choices) {
        while (true) {
            System.out.print(label + " ID (Enter cancels the whole demand): ");
            if (!scanner.hasNextLine()) {
                return null;
            }
            String value = scanner.nextLine().trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                long id = Long.parseLong(value);
                if (choices.containsKey(id)) {
                    return id;
                }
            } catch (NumberFormatException exception) {
                // The message below also handles IDs that are not in the list.
            }
            System.out.println("Please enter an ID from the displayed list.");
        }
    }

    private static NewDemandItem readItem(Scanner scanner, CoffeeProduct product) {
        while (true) {
            System.out.print("Quantity in " + product.getUnit()
                + " (example 12.50; Enter cancels): ");
            if (!scanner.hasNextLine()) {
                return null;
            }
            String value = scanner.nextLine().trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                BigDecimal quantity = new BigDecimal(value);
                return new NewDemandItem(product.getProductId(), quantity, null);
            } catch (IllegalArgumentException exception) {
                System.out.println("Enter a positive quantity that fits DECIMAL(15,2)."
                    + " Use a decimal point, for example 12.50.");
            }
        }
    }

    private static boolean readYes(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return false;
            }
            String value = scanner.nextLine().trim();
            if (value.equalsIgnoreCase("y")) {
                return true;
            }
            if (value.isEmpty() || value.equalsIgnoreCase("n")) {
                return false;
            }
            System.out.println("Please enter y or n.");
        }
    }

    private static void cancel() {
        System.out.println("Creation cancelled. Nothing was saved.");
    }
}
