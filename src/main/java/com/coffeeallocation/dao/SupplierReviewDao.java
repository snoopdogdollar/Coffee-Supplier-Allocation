package com.coffeeallocation.dao;

import com.coffeeallocation.model.SupplierReview;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public final class SupplierReviewDao {
    private final Connection connection;

    public SupplierReviewDao(Connection connection) {
        this.connection = connection;
    }

    public List<SupplierReview> findByOrder(long shopId, long orderId)
            throws SQLException {
        String sql = """
                SELECT r.review_id, r.order_id, r.supplier_id,
                       r.quality_ok, r.quantity_ok, r.on_time_ok,
                       r.comment, r.created_at
                FROM supplier_reviews r
                JOIN orders o
                  ON o.order_id = r.order_id AND o.supplier_id = r.supplier_id
                WHERE o.shop_id = ? AND o.order_id = ?
                ORDER BY r.review_id
                """;
        List<SupplierReview> reviews = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, shopId);
            statement.setLong(2, orderId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    reviews.add(new SupplierReview(
                            rows.getLong("review_id"),
                            rows.getLong("order_id"),
                            rows.getLong("supplier_id"),
                            rows.getBoolean("quality_ok"),
                            rows.getBoolean("quantity_ok"),
                            rows.getBoolean("on_time_ok"),
                            rows.getString("comment"),
                            rows.getTimestamp("created_at").toLocalDateTime()
                    ));
                }
            }
        }
        return List.copyOf(reviews);
    }

    public OptionalLong lockCompletedOrder(long shopId, long orderId)
            throws SQLException {
        requireTransaction();
        String sql = """
                SELECT supplier_id
                FROM orders
                WHERE shop_id = ? AND order_id = ? AND status = 'COMPLETED'
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, shopId);
            statement.setLong(2, orderId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? OptionalLong.of(rows.getLong("supplier_id"))
                        : OptionalLong.empty();
            }
        }
    }

    public long insert(long orderId, long supplierId,
            boolean qualityOk, boolean quantityOk, boolean onTimeOk,
            String comment) throws SQLException {
        requireTransaction();
        String sql = """
                INSERT INTO supplier_reviews (
                    order_id, supplier_id, quality_ok, quantity_ok,
                    on_time_ok, comment
                )
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING review_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, orderId);
            statement.setLong(2, supplierId);
            statement.setBoolean(3, qualityOk);
            statement.setBoolean(4, quantityOk);
            statement.setBoolean(5, onTimeOk);
            statement.setString(6, comment);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Insert did not return a review ID.");
                }
                return rows.getLong("review_id");
            }
        }
    }

    private void requireTransaction() throws SQLException {
        if (connection.getAutoCommit()) {
            throw new SQLException(
                    "Service must start a transaction before writing a review."
            );
        }
    }
}
