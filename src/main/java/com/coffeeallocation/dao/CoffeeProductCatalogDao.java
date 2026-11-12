package com.coffeeallocation.dao;

import com.coffeeallocation.model.CoffeeProductDetails;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Shared product administration; existing active-catalog readers remain unchanged. */
public final class CoffeeProductCatalogDao {
    private final Connection connection;

    public CoffeeProductCatalogDao(Connection connection) { this.connection = connection; }

    public List<CoffeeProductDetails> findAll() throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT product_id, name, description, unit, variety, is_active, created_at
                FROM coffee_products ORDER BY name, product_id
                """); var rows = statement.executeQuery()) {
            List<CoffeeProductDetails> products = new ArrayList<>();
            while (rows.next()) {
                products.add(new CoffeeProductDetails(rows.getLong("product_id"), rows.getString("name"),
                        rows.getString("description"), rows.getString("unit"), rows.getString("variety"),
                        rows.getBoolean("is_active"), rows.getTimestamp("created_at").toLocalDateTime()));
            }
            return List.copyOf(products);
        }
    }

    public long insert(String name, String description, String unit, String variety) throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement("""
                INSERT INTO coffee_products (name, description, unit, variety)
                VALUES (?, ?, ?, ?) RETURNING product_id
                """)) {
            statement.setString(1, name);
            statement.setString(2, description);
            statement.setString(3, unit);
            statement.setString(4, variety);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("The inserted product ID was not returned.");
                return rows.getLong(1);
            }
        }
    }

    public void changeStatus(long productId, boolean expectedActive) throws SQLException {
        requireTransaction();
        try (var statement = connection.prepareStatement("""
                UPDATE coffee_products SET is_active = ?
                WHERE product_id = ? AND is_active = ?
                """)) {
            statement.setBoolean(1, !expectedActive);
            statement.setLong(2, productId);
            statement.setBoolean(3, expectedActive);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("The product was removed or its status changed. Refresh the catalog before trying again.");
            }
        }
    }

    private void requireTransaction() throws SQLException {
        if (connection.getAutoCommit()) throw new SQLException("Catalog writes require a service transaction.");
    }
}
