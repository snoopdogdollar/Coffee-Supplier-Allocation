package com.coffeeallocation;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.model.CoffeeProduct;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class Main {

    public static void main(String[] args) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            CoffeeProductDao productDao = new CoffeeProductDao(connection);

            List<CoffeeProduct> products = productDao.findActive();

            for (CoffeeProduct product : products) {
                System.out.println(
                    product.getName() + " - " + product.getUnit()
                );
            }

            System.out.println(
                "Total active products: " + products.size()
            );
        }
    }
}