package com.coffeeallocation;

import com.coffeeallocation.console.AllocationConfirmationConsole;
import com.coffeeallocation.console.OrderCompletionConsole;
import com.coffeeallocation.console.OrderConsole;
import com.coffeeallocation.console.SupplierReviewConsole;
import com.coffeeallocation.console.SupplierHistoryConsole;
import com.coffeeallocation.console.SharedAllocationConsole;
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
import com.coffeeallocation.model.DeliveryWeek;
import com.coffeeallocation.model.DemandExpiryPolicy;
import com.coffeeallocation.console.DeliveryWeekConsole;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class Main {

	public static void main(String[] args) {
		DeliveryWeek selectedWeek = DeliveryWeek.from(LocalDate.now(DemandExpiryPolicy.BUSINESS_ZONE));
		try (Scanner scanner = new Scanner(System.in)) {
			while (true) {
				printMenu(selectedWeek);

				if (!scanner.hasNextLine()) {
					return;
				}

				String choice = scanner.nextLine().trim();

				try {
					switch (choice) {
					case "1" -> showProducts();
					case "2" -> showWeeklySupply(scanner, selectedWeek);
					case "3" -> showOpenDemandItems(selectedWeek);
					case "4" -> showShops();
					case "5" -> CreateDemandConsole.run(scanner, selectedWeek.year(), selectedWeek.week());
					case "6" -> MatchingConsole.run(scanner, selectedWeek.year(), selectedWeek.week());
					case "7" -> SharedAllocationConsole.run(selectedWeek.year(), selectedWeek.week());
					case "8" -> AllocationConfirmationConsole.run(scanner, selectedWeek.year(), selectedWeek.week());
					case "9" -> OrderConsole.run(scanner);
					case "10" -> OrderCompletionConsole.run(scanner);
					case "11" -> SupplierReviewConsole.run(scanner);
					case "12" -> SupplierHistoryConsole.run(scanner);
					case "13" -> selectedWeek = DeliveryWeekConsole.select(scanner, System.out, selectedWeek);
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
				} catch (IllegalArgumentException exception) {
					System.out.println("Request rejected: " + exception.getMessage());
				}
			}
		}
	}

	private static void printMenu(DeliveryWeek selectedWeek) {
		System.out.println();
		System.out.println("=== Coffee Supplier Allocation ===");
		System.out.println("Selected delivery period: ISO week " + selectedWeek.week() + "/" + selectedWeek.year());
		System.out.println("Startup default: current week in Asia/Ho_Chi_Minh. Use menu 13 to change it.");
		System.out.println("1. Show active products");
		System.out.println("2. Show weekly supply for a product");
		System.out.println("3. Show open demand items");
		System.out.println("4. Show active coffee shops");
		System.out.println("5. Create OPEN demand");
		System.out.println("6. Match an OPEN demand item to suppliers");
		System.out.println("7. Preview shared allocation for OPEN demand items");
		System.out.println("8. Preview and confirm allocations / create orders");
		System.out.println("9. View orders and order details");
		System.out.println("10. Complete a pending order");
		System.out.println("11. Review supplier / view order reviews");
		System.out.println("12. View supplier review history");
		System.out.println("13. Change delivery ISO year/week");
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

	private static void showWeeklySupply(Scanner scanner, DeliveryWeek selectedWeek) throws SQLException {
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
			    product.getProductId(), selectedWeek.year(), selectedWeek.week()
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

	private static void showOpenDemandItems(DeliveryWeek selectedWeek) throws SQLException {
		List<OpenDemandItem> items;
		try (Connection connection = DatabaseConnection.getConnection()) {
			items = new DemandDao(connection).findOpenItemsForWeek(
			    selectedWeek.year(), selectedWeek.week()
			);
		}

		for (OpenDemandItem item : items) {
			System.out.println(
			    "Demand " + item.getDemandId()
			    + " | Item " + item.getDemandItemId()
			    + " | " + item.getShopName()
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
