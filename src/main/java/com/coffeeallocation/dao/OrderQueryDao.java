package com.coffeeallocation.dao;

import com.coffeeallocation.model.OrderView;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderQueryDao {
    private final Connection connection;

    private static final String SELECT_SUMMARY = """
            SELECT o.order_id, o.order_date,
                   cs.name AS shop_name,
                   s.name AS supplier_name,
                   o.allocation_id, o.status,
                   COUNT(oi.order_item_id) AS item_count,
                   COALESCE(SUM(oi.total_price), 0) AS total_price
            FROM orders o
            JOIN coffee_shops cs ON cs.shop_id = o.shop_id
            JOIN suppliers s ON s.supplier_id = o.supplier_id
            LEFT JOIN order_items oi ON oi.order_id = o.order_id
            """;

    private static final String GROUP_SUMMARY = """
            GROUP BY o.order_id, o.order_date, cs.name, s.name,
                     o.allocation_id, o.status
            """;

    public OrderQueryDao(Connection connection) {
        this.connection = connection;
    }

    public List<OrderView.BuyerOrder> findBuyerPage(long shopId, Long beforeId) throws SQLException {
        return findPartyPage("o.shop_id", shopId, beforeId);
    }

    public List<OrderView.BuyerOrder> findSupplierPage(long supplierId, Long beforeId) throws SQLException {
        return findPartyPage("o.supplier_id", supplierId, beforeId);
    }

    // The two public entry points above supply fixed SQL identifiers; values are bound.
    private List<OrderView.BuyerOrder> findPartyPage(String ownerColumn, long ownerId, Long beforeId)
            throws SQLException {
        String sql = """
                WITH page AS (
                    SELECT o.order_id, o.allocation_id, o.supplier_id, o.shop_id, o.order_date, o.status
                    FROM orders o
                    WHERE
                """ + ownerColumn + " = ?\n" + (beforeId == null ? "" : " AND o.order_id < ?\n") + """
                    ORDER BY o.order_id DESC
                    LIMIT 21
                )
                SELECT page.order_id, di.demand_id, page.supplier_id, s.name AS supplier_name,
                       page.shop_id, cs.name AS shop_name,
                       page.order_date, page.status,
                       EXISTS (
                           SELECT 1 FROM supplier_reviews r
                           WHERE r.order_id = page.order_id AND r.supplier_id = page.supplier_id
                       ) AS reviewed,
                       oi.order_item_id, oi.product_id, p.name AS product_name, p.unit,
                       oi.quantity, oi.unit_price, oi.total_price
                FROM page
                JOIN allocations a ON a.allocation_id = page.allocation_id
                JOIN demand_items di ON di.demand_item_id = a.demand_item_id
                JOIN suppliers s ON s.supplier_id = page.supplier_id
                JOIN coffee_shops cs ON cs.shop_id = page.shop_id
                LEFT JOIN order_items oi ON oi.order_id = page.order_id
                LEFT JOIN coffee_products p ON p.product_id = oi.product_id
                ORDER BY page.order_id DESC, oi.order_item_id
                """;

        Map<Long, OrderView.BuyerSummary> summaries = new LinkedHashMap<>();
        Map<Long, List<OrderView.Line>> items = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, ownerId);
            if (beforeId != null) {
                statement.setLong(2, beforeId);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long orderId = rows.getLong("order_id");
                    summaries.putIfAbsent(orderId, new OrderView.BuyerSummary(orderId,
                            rows.getLong("demand_id"), rows.getLong("supplier_id"),
                            rows.getString("supplier_name"), rows.getDate("order_date").toLocalDate(),
                            rows.getString("status"), rows.getBoolean("reviewed"),
                            rows.getLong("shop_id"), rows.getString("shop_name")));
                    if (rows.getObject("order_item_id") != null) {
                        items.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(
                                new OrderView.Line(rows.getLong("product_id"), rows.getString("product_name"),
                                        rows.getString("unit"), rows.getBigDecimal("quantity"),
                                        rows.getBigDecimal("unit_price"), rows.getBigDecimal("total_price")));
                    }
                }
            }
        }
        return summaries.values().stream()
                .map(summary -> new OrderView.BuyerOrder(summary,
                        items.getOrDefault(summary.orderId(), List.of())))
                .toList();
    }

    public List<OrderView.Summary> findPage(Long beforeId)
            throws SQLException {

        String sql = SELECT_SUMMARY
                + (beforeId == null ? "" : "WHERE o.order_id < ?\n")
                + GROUP_SUMMARY
                + "ORDER BY o.order_id DESC LIMIT 20";

        List<OrderView.Summary> orders = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            if (beforeId != null) {
                statement.setLong(1, beforeId);
            }

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    orders.add(mapSummary(rows));
                }
            }
        }

        return List.copyOf(orders);
    }

    public Optional<OrderView.Summary> findSummary(long orderId)
            throws SQLException {

        String sql = SELECT_SUMMARY
                + "WHERE o.order_id = ?\n"
                + GROUP_SUMMARY;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setLong(1, orderId);

            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? Optional.of(mapSummary(rows))
                        : Optional.empty();
            }
        }
    }

    public List<OrderView.Line> findItems(long orderId)
            throws SQLException {

        String sql = """
                SELECT oi.product_id,
                       p.name AS product_name,
                       p.unit,
                       oi.quantity,
                       oi.unit_price,
                       oi.total_price
                FROM order_items oi
                JOIN coffee_products p
                    ON p.product_id = oi.product_id
                WHERE oi.order_id = ?
                ORDER BY oi.order_item_id
                """;

        List<OrderView.Line> items = new ArrayList<>();

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setLong(1, orderId);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(new OrderView.Line(
                            rows.getLong("product_id"),
                            rows.getString("product_name"),
                            rows.getString("unit"),
                            rows.getBigDecimal("quantity"),
                            rows.getBigDecimal("unit_price"),
                            rows.getBigDecimal("total_price")
                    ));
                }
            }
        }

        return List.copyOf(items);
    }

    private static OrderView.Summary mapSummary(ResultSet rows)
            throws SQLException {

        return new OrderView.Summary(
                rows.getLong("order_id"),
                rows.getDate("order_date").toLocalDate(),
                rows.getString("shop_name"),
                rows.getString("supplier_name"),
                rows.getLong("allocation_id"),
                rows.getString("status"),
                rows.getInt("item_count"),
                rows.getBigDecimal("total_price")
        );
    }
}
