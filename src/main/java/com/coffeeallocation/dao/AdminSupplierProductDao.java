package com.coffeeallocation.dao;

import com.coffeeallocation.model.AdminSupplierProductView.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class AdminSupplierProductDao {
    private final Connection connection;
    public AdminSupplierProductDao(Connection connection) { this.connection = connection; }
    public List<Row> page(String supplier, String product, Long beforeId) throws SQLException {
        String sql = """
                SELECT sp.supplier_product_id, sp.supplier_id, s.name AS supplier_name,
                       sp.product_id, p.name AS product_name, p.unit,
                       sp.is_active AS offering_active, s.is_active AS supplier_active, p.is_active AS product_active,
                       EXISTS (SELECT 1 FROM supplier_weekly_supply sw
                               WHERE sw.supplier_product_id = sp.supplier_product_id) AS has_supply
                FROM supplier_products sp
                JOIN suppliers s ON s.supplier_id = sp.supplier_id
                JOIN coffee_products p ON p.product_id = sp.product_id
                WHERE strpos(lower(concat_ws(' ', s.supplier_id, s.name)), lower(?)) > 0
                  AND strpos(lower(concat_ws(' ', p.product_id, p.name)), lower(?)) > 0
                  AND (?::BIGINT IS NULL OR sp.supplier_product_id < ?)
                ORDER BY sp.supplier_product_id DESC LIMIT 21
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, supplier);statement.setString(2, product);
            statement.setObject(3, beforeId, Types.BIGINT);statement.setObject(4, beforeId, Types.BIGINT);
            try (var rows = statement.executeQuery()) {
                List<Row> result = new ArrayList<>();
                while (rows.next()) result.add(new Row(rows.getLong("supplier_product_id"), rows.getLong("supplier_id"),
                        rows.getString("supplier_name"), rows.getLong("product_id"), rows.getString("product_name"),
                        rows.getString("unit"), rows.getBoolean("offering_active"), rows.getBoolean("supplier_active"),
                        rows.getBoolean("product_active"), rows.getBoolean("has_supply")));
                return List.copyOf(result);
            }
        }
    }
    public List<Supplier> activeSuppliers() throws SQLException {
        try (var statement = connection.prepareStatement("SELECT supplier_id, name FROM suppliers WHERE is_active ORDER BY name, supplier_id");
             var rows = statement.executeQuery()) {
            List<Supplier> result = new ArrayList<>();
            while (rows.next()) result.add(new Supplier(rows.getLong(1), rows.getString(2)));
            return List.copyOf(result);
        }
    }
}
