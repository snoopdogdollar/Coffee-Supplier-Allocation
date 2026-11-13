package com.coffeeallocation.dao;

import com.coffeeallocation.model.AdminReviewView;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class AdminReviewDao {
    private final Connection connection;
    public AdminReviewDao(Connection connection) { this.connection=connection; }
    public List<AdminReviewView.Row> page(String supplier, String shop, Long orderId, Long beforeId) throws SQLException {
        String sql="""
                SELECT r.review_id, r.order_id, o.shop_id, cs.name AS shop_name,
                       r.supplier_id, s.name AS supplier_name, o.status AS order_status,
                       r.quality_ok, r.quantity_ok, r.on_time_ok, r.comment, r.created_at
                FROM supplier_reviews r
                JOIN orders o ON o.order_id = r.order_id AND o.supplier_id = r.supplier_id
                JOIN coffee_shops cs ON cs.shop_id = o.shop_id
                JOIN suppliers s ON s.supplier_id = r.supplier_id
                WHERE strpos(lower(concat_ws(' ', r.supplier_id, s.name)), lower(?)) > 0
                  AND strpos(lower(concat_ws(' ', o.shop_id, cs.name)), lower(?)) > 0
                  AND (?::BIGINT IS NULL OR r.order_id = ?)
                  AND (?::BIGINT IS NULL OR r.review_id < ?)
                ORDER BY r.review_id DESC LIMIT 21
                """;
        try (var statement=connection.prepareStatement(sql)) {
            statement.setString(1,supplier);statement.setString(2,shop);
            statement.setObject(3,orderId,Types.BIGINT);statement.setObject(4,orderId,Types.BIGINT);
            statement.setObject(5,beforeId,Types.BIGINT);statement.setObject(6,beforeId,Types.BIGINT);
            try (var rows=statement.executeQuery()) {
                List<AdminReviewView.Row> result=new ArrayList<>();
                while(rows.next()) result.add(new AdminReviewView.Row(rows.getLong("review_id"),rows.getLong("order_id"),
                        rows.getLong("shop_id"),rows.getString("shop_name"),rows.getLong("supplier_id"),rows.getString("supplier_name"),
                        rows.getString("order_status"),rows.getBoolean("quality_ok"),rows.getBoolean("quantity_ok"),
                        rows.getBoolean("on_time_ok"),rows.getString("comment"),rows.getTimestamp("created_at").toLocalDateTime()));
                return List.copyOf(result);
            }
        }
    }
}
