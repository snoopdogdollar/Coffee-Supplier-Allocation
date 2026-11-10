package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.CoffeeShopDao;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.CoffeeShop;
import com.coffeeallocation.model.OpenDemandItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

/** Read operations used by the desktop presentation layer. */
public final class DesktopCatalogService {
    public record Catalog(List<CoffeeShop> shops, List<CoffeeProduct> products) {
        public Catalog {
            shops = List.copyOf(shops);
            products = List.copyOf(products);
        }
    }

    public Catalog loadCatalog() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            return new Catalog(new CoffeeShopDao(connection).findActive(),
                    new CoffeeProductDao(connection).findActive());
        }
    }

    public List<CoffeeProduct> findProducts() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            return List.copyOf(new CoffeeProductDao(connection).findActive());
        }
    }

    public List<CoffeeShop> findShops() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            return List.copyOf(new CoffeeShopDao(connection).findActive());
        }
    }

    public List<OpenDemandItem> findOpenItems(int year, int week) throws SQLException {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("Năm ISO phải từ 1 đến 9999.");
        }
        int lastWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) {
            throw new IllegalArgumentException("Tuần ISO không hợp lệ cho năm " + year + ".");
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            return List.copyOf(new DemandDao(connection).findOpenItemsForWeek(year, week));
        }
    }
}
