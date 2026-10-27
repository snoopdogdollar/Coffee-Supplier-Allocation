package com.coffeeallocation;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.console.MatchingConsole;
import com.coffeeallocation.console.CreateDemandConsole;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.dao.WeeklySupplyDao;
import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.OpenDemandItem;
import com.coffeeallocation.dao.CoffeeShopDao;
import com.coffeeallocation.model.CoffeeShop;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class Main {

	// Fixed period used by the demo seed data.
	private static final int DEMO_YEAR = 2026;
	private static final int DEMO_WEEK = 42;

	public static void main(String[] args) {
		try (Scanner scanner = new Scanner(System.in)) {
			while (true) {
				printMenu();

				if (!scanner.hasNextLine()) {
					return;
				}

				String choice = scanner.nextLine().trim();

				try {
					switch (choice) {
					case "1" -> showProducts();
					case "2" -> showWeeklySupply(scanner);
					case "3" -> showOpenDemandItems();
					case "4" -> showShops();
					case "5" -> CreateDemandConsole.run(scanner, DEMO_YEAR, DEMO_WEEK);
					case "6" -> MatchingConsole.run(scanner, DEMO_YEAR, DEMO_WEEK);
					case "0" -> {
							    System.out.println("Goodbye!");
							    return;
							}
							default -> System.out.println("Please choose one of the options.");
					}
				} catch (SQLException exception) {
					System.out.println("Database error: " + exception.getMessage());
				} catch (IllegalStateException exception) {
					System.out.println("Configuration error: " + exception.getMessage());
				}
			}
		}
	}

	private static void printMenu() {
		System.out.println();
		System.out.println("=== Coffee Supplier Allocation ===");
		System.out.println("Demo period: ISO week " + DEMO_WEEK + "/" + DEMO_YEAR);
		System.out.println("1. Show active products");
		System.out.println("2. Show weekly supply for a product");
		System.out.println("3. Show open demand items");
		System.out.println("4. Show active coffee shops");
		System.out.println("5. Create demo OPEN demand");
		System.out.println("6. Match an OPEN demand item to suppliers");
		System.out.println("0. Exit");
		System.out.print("Choose: ");
	}

	private static List<CoffeeProduct> loadProducts() throws SQLException {
		try (Connection connection = DatabaseConnection.getConnection()) {
			return new CoffeeProductDao(connection).findActive();
		}
	}

	private static void printProducts(List<CoffeeProduct> products) {
		for (CoffeeProduct product : products) {
			System.out.println(
			    product.getProductId() + " | " + product.getName()
			    + " | " + product.getUnit()
			);
		}
	}

	private static void showProducts() throws SQLException {
		List<CoffeeProduct> products = loadProducts();
		printProducts(products);
		System.out.println("Total active products: " + products.size());
	}

	private static CoffeeProduct chooseProduct(
	    Scanner scanner, List<CoffeeProduct> products) {

		while (true) {
			System.out.print("Product ID (Enter to return): ");

			if (!scanner.hasNextLine()) {
				return null;
			}

			String value = scanner.nextLine().trim();
			if (value.isEmpty()) {
				return null;
			}

			long productId;
			try {
				productId = Long.parseLong(value);
			} catch (NumberFormatException exception) {
				System.out.println("Please enter a numeric product ID from the list.");
				continue;
			}

			for (CoffeeProduct product : products) {
				if (product.getProductId() == productId) {
					return product;
				}
			}

			System.out.println("Choose an active product ID from the list.");
		}
	}

	private static void showWeeklySupply(Scanner scanner) throws SQLException {
		// loadProducts closes its connection before waiting for user input.
		List<CoffeeProduct> products = loadProducts();
		if (products.isEmpty()) {
			System.out.println("No active products found.");
			return;
		}

		printProducts(products);
		CoffeeProduct product = chooseProduct(scanner, products);
		if (product == null) {
			return;
		}

		List<AvailableSupply> supplies;
		try (Connection connection = DatabaseConnection.getConnection()) {
			supplies = new WeeklySupplyDao(connection).findEligible(
			    product.getProductId(), DEMO_YEAR, DEMO_WEEK
			);
		}

		System.out.println("Available supply for " + product.getName() + ":");
		BigDecimal total = BigDecimal.ZERO;

		for (AvailableSupply supply : supplies) {
			System.out.println(
			    supply.getSupplierName()
			    + " | " + supply.getRemainingQuantity() + " " + product.getUnit()
			    + " | " + supply.getUnitPrice() + " VND/" + product.getUnit()
			);
			total = total.add(supply.getRemainingQuantity());
		}

		if (supplies.isEmpty()) {
			System.out.println("No eligible supply found for this product and week.");
		}

		System.out.println("Total available: " + total + " " + product.getUnit());
	}

	private static void showOpenDemandItems() throws SQLException {
		List<OpenDemandItem> items;
		try (Connection connection = DatabaseConnection.getConnection()) {
			items = new DemandDao(connection).findOpenItemsForWeek(
			    DEMO_YEAR, DEMO_WEEK
			);
		}

		for (OpenDemandItem item : items) {
			System.out.println(
			    item.getShopName()
			    + " | " + item.getProductName()
			    + " | " + item.getQuantity() + " " + item.getUnit()
			);
		}

		System.out.println("Total open demand items: " + items.size());
	}

	private static List<CoffeeShop> loadShops() throws SQLException {
		try (Connection connection = DatabaseConnection.getConnection()) {
			return new CoffeeShopDao(connection).findActive();
		}
	}

	private static void showShops() throws SQLException {
		List<CoffeeShop> shops = loadShops();

		for (CoffeeShop shop : shops) {
			System.out.println(
			    shop.getShopId() + " | " + shop.getName()
			);
		}

		System.out.println(
		    "Total active coffee shops: " + shops.size()
		);
	}
}
