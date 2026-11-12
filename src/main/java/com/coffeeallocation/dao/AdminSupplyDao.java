package com.coffeeallocation.dao;

import com.coffeeallocation.model.AdminSupplyView;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class AdminSupplyDao {
    private final Connection connection;
    public AdminSupplyDao(Connection connection) { this.connection = connection; }
    public List<AdminSupplyView.Row> page(Integer year, Integer week, String supplier, String product, Long beforeId)
            throws SQLException {
        String sql = """
                SELECT sw.supply_id, sw.supplier_product_id, s.supplier_id, s.name AS supplier_name,
                       p.product_id, p.name AS product_name, p.unit, sw.year, sw.week,
                       sw.initial_quantity, sw.remaining_quantity, sw.unit_price,
                       s.is_active AS supplier_active, p.is_active AS product_active, sp.is_active AS offering_active
                FROM supplier_weekly_supply sw
                JOIN supplier_products sp ON sp.supplier_product_id = sw.supplier_product_id
                JOIN suppliers s ON s.supplier_id = sp.supplier_id
                JOIN coffee_products p ON p.product_id = sp.product_id
                WHERE (?::INTEGER IS NULL OR sw.year = ?)
                  AND (?::INTEGER IS NULL OR sw.week = ?)
                  AND strpos(lower(concat_ws(' ', s.supplier_id, s.name)), lower(?)) > 0
                  AND strpos(lower(concat_ws(' ', p.product_id, p.name)), lower(?)) > 0
                  AND (?::BIGINT IS NULL OR sw.supply_id < ?)
                ORDER BY sw.supply_id DESC LIMIT 21
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, year, Types.INTEGER);statement.setObject(2, year, Types.INTEGER);
            statement.setObject(3, week, Types.INTEGER);statement.setObject(4, week, Types.INTEGER);
            statement.setString(5, supplier);statement.setString(6, product);
            statement.setObject(7, beforeId, Types.BIGINT);statement.setObject(8, beforeId, Types.BIGINT);
            try (var rows = statement.executeQuery()) {
                List<AdminSupplyView.Row> result = new ArrayList<>();
                while (rows.next()) result.add(new AdminSupplyView.Row(rows.getLong("supply_id"),
                        rows.getLong("supplier_product_id"), rows.getLong("supplier_id"), rows.getString("supplier_name"),
                        rows.getLong("product_id"), rows.getString("product_name"), rows.getString("unit"),
                        rows.getInt("year"), rows.getInt("week"), rows.getBigDecimal("initial_quantity"),
                        rows.getBigDecimal("remaining_quantity"), rows.getBigDecimal("unit_price"),
                        rows.getBoolean("supplier_active"), rows.getBoolean("product_active"), rows.getBoolean("offering_active")));
                return List.copyOf(result);
            }
        }
    }
}
