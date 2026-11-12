package com.coffeeallocation.dao;

import com.coffeeallocation.model.AdminActivityView.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AdminActivityDao {
    private final Connection connection;
    public AdminActivityDao(Connection connection) { this.connection = connection; }

    public List<Order> orders(String query, Long beforeId) throws SQLException {
        String sql = """
                WITH page AS (
                    SELECT o.order_id, di.demand_id, o.order_date, o.shop_id, cs.name AS shop_name,
                           o.supplier_id, s.name AS supplier_name, o.status
                    FROM orders o
                    JOIN coffee_shops cs ON cs.shop_id = o.shop_id
                    JOIN suppliers s ON s.supplier_id = o.supplier_id
                    JOIN allocations a ON a.allocation_id = o.allocation_id
                    JOIN demand_items di ON di.demand_item_id = a.demand_item_id
                    WHERE (?::BIGINT IS NULL OR o.order_id < ?)
                      AND (strpos(lower(concat_ws(' ', o.order_id, di.demand_id, o.shop_id, cs.name,
                                   o.supplier_id, s.name, o.status, o.order_date)), lower(?)) > 0
                           OR EXISTS (SELECT 1 FROM order_items oi JOIN coffee_products p USING(product_id)
                                      WHERE oi.order_id = o.order_id
                                        AND strpos(lower(concat_ws(' ', p.product_id, p.name)), lower(?)) > 0))
                    ORDER BY o.order_id DESC LIMIT 21
                )
                SELECT page.*, totals.item_count, totals.total_price
                FROM page CROSS JOIN LATERAL (
                    SELECT COUNT(*) AS item_count, COALESCE(SUM(oi.total_price), 0) AS total_price
                    FROM order_items oi WHERE oi.order_id = page.order_id
                ) totals ORDER BY page.order_id DESC
                """;
        try (var statement = connection.prepareStatement(sql)) {
            bindPage(statement, query, beforeId);
            try (var rows = statement.executeQuery()) {
                List<Order> result = new ArrayList<>();
                while (rows.next()) result.add(new Order(rows.getLong("order_id"), rows.getLong("demand_id"),
                        rows.getDate("order_date").toLocalDate(), rows.getLong("shop_id"), rows.getString("shop_name"),
                        rows.getLong("supplier_id"), rows.getString("supplier_name"), rows.getString("status"),
                        rows.getInt("item_count"), rows.getBigDecimal("total_price")));
                return List.copyOf(result);
            }
        }
    }

    private static final String DEMAND_SUMMARY = """
            SELECT d.demand_id, d.request_date, d.required_year, d.required_week, d.shop_id,
                   cs.name AS shop_name, d.status, d.note,
                   (SELECT COUNT(*) FROM demand_items di WHERE di.demand_id = d.demand_id) AS item_count
            FROM demands d JOIN coffee_shops cs ON cs.shop_id = d.shop_id
            """;

    public List<Demand> demands(String query, Long beforeId) throws SQLException {
        String sql = DEMAND_SUMMARY + """
                WHERE (?::BIGINT IS NULL OR d.demand_id < ?)
                  AND (strpos(lower(concat_ws(' ', d.demand_id, d.shop_id, cs.name, d.status, d.request_date,
                               d.required_year || '-W' || lpad(d.required_week::TEXT, 2, '0'), d.note)), lower(?)) > 0
                       OR EXISTS (SELECT 1 FROM demand_items di JOIN coffee_products p USING(product_id)
                                  WHERE di.demand_id = d.demand_id
                                    AND strpos(lower(concat_ws(' ', p.product_id, p.name)), lower(?)) > 0))
                ORDER BY d.demand_id DESC LIMIT 21
                """;
        try (var statement = connection.prepareStatement(sql)) {
            bindPage(statement, query, beforeId);
            try (var rows = statement.executeQuery()) {
                List<Demand> result = new ArrayList<>();
                while (rows.next()) result.add(demand(rows));
                return List.copyOf(result);
            }
        }
    }

    public Optional<Demand> findDemand(long id) throws SQLException {
        try (var statement = connection.prepareStatement(DEMAND_SUMMARY + " WHERE d.demand_id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) { return rows.next() ? Optional.of(demand(rows)) : Optional.empty(); }
        }
    }

    public List<DemandLine> demandItems(long id) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT di.product_id, p.name, p.unit, di.quantity, di.note
                FROM demand_items di JOIN coffee_products p ON p.product_id = di.product_id
                WHERE di.demand_id = ? ORDER BY di.demand_item_id
                """)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                List<DemandLine> result = new ArrayList<>();
                while (rows.next()) result.add(new DemandLine(rows.getLong("product_id"), rows.getString("name"),
                        rows.getString("unit"), rows.getBigDecimal("quantity"), rows.getString("note")));
                return List.copyOf(result);
            }
        }
    }

    private static Demand demand(ResultSet row) throws SQLException {
        return new Demand(row.getLong("demand_id"), row.getDate("request_date").toLocalDate(),
                row.getInt("required_year"), row.getInt("required_week"), row.getLong("shop_id"),
                row.getString("shop_name"), row.getString("status"), row.getInt("item_count"), row.getString("note"));
    }
    private static void bindPage(PreparedStatement statement, String query, Long beforeId) throws SQLException {
        statement.setObject(1, beforeId, Types.BIGINT);
        statement.setObject(2, beforeId, Types.BIGINT);
        statement.setString(3, query);
        statement.setString(4, query);
    }
}
