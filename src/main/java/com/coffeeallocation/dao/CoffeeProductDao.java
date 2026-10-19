package com.coffeeallocation.dao;

import com.coffeeallocation.model.CoffeeProduct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CoffeeProductDao {

    private final Connection connection;

    public CoffeeProductDao(Connection connection) {
        this.connection = connection;
    }

    public List<CoffeeProduct> findActive() throws SQLException {
        String sql = """
                SELECT product_id, name, unit
                FROM coffee_products
                WHERE is_active = ?
                ORDER BY name, product_id
                """;

        List<CoffeeProduct> products = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, true);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CoffeeProduct product = new CoffeeProduct(
                        resultSet.getLong("product_id"),
                        resultSet.getString("name"),
                        resultSet.getString("unit")
                    );

                    products.add(product);
                }
            }
        }

        return products;
    }
}