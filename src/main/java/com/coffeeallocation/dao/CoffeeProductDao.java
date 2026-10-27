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
                    products.add(new CoffeeProduct(
                        resultSet.getLong("product_id"),
                        resultSet.getString("name"), resultSet.getString("unit")
                    ));
                }
            }
        }
        return products;
    }

    public boolean lockActiveById(long productId) throws SQLException { //postgresql has a FOR SHARE clause that allows you to lock a row for reading, preventing other transactions from modifying it until the current transaction is complete. This is useful when you want to ensure that the data you're reading remains consistent while you perform some operations on it.
        if (connection.getAutoCommit()) {
            throw new SQLException("Locking a product requires a service transaction.");
        }

        String sql = """
                SELECT product_id
                FROM coffee_products
                WHERE product_id = ? AND is_active = TRUE
                FOR SHARE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
