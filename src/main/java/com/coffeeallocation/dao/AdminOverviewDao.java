package com.coffeeallocation.dao;

import com.coffeeallocation.model.AdminOverviewView.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class AdminOverviewDao {
    private final Connection connection;
    public AdminOverviewDao(Connection connection) {this.connection=connection;}
    public Totals totals() throws SQLException {
        try(var statement=connection.prepareStatement("""
                SELECT (SELECT COUNT(*) FROM orders) AS total_orders,
                       (SELECT COUNT(*) FROM orders WHERE status = 'PENDING') AS pending_orders,
                       (SELECT COUNT(*) FROM suppliers WHERE is_active) AS active_suppliers
                """);var row=statement.executeQuery()) {
            if(!row.next())throw new SQLException("Dashboard totals were not returned.");
            return new Totals(row.getLong("total_orders"),row.getLong("pending_orders"),row.getLong("active_suppliers"));
        }
    }
    public List<RecentOrder> recentOrders() throws SQLException {
        String sql="""
                WITH recent AS (
                    SELECT order_id, order_date, shop_id, supplier_id, status
                    FROM orders ORDER BY order_id DESC LIMIT 10
                )
                SELECT recent.*, cs.name AS shop_name, s.name AS supplier_name,
                       totals.item_count, totals.total_price
                FROM recent
                JOIN coffee_shops cs ON cs.shop_id = recent.shop_id
                JOIN suppliers s ON s.supplier_id = recent.supplier_id
                CROSS JOIN LATERAL (
                    SELECT COUNT(*) AS item_count, COALESCE(SUM(oi.total_price), 0) AS total_price
                    FROM order_items oi WHERE oi.order_id = recent.order_id
                ) totals
                ORDER BY recent.order_id DESC
                """;
        try(var statement=connection.prepareStatement(sql);var rows=statement.executeQuery()) {
            List<RecentOrder> result=new ArrayList<>();
            while(rows.next())result.add(new RecentOrder(rows.getLong("order_id"),rows.getDate("order_date").toLocalDate(),
                    rows.getLong("shop_id"),rows.getString("shop_name"),rows.getLong("supplier_id"),rows.getString("supplier_name"),
                    rows.getString("status"),rows.getInt("item_count"),rows.getBigDecimal("total_price")));
            return List.copyOf(result);
        }
    }
}
