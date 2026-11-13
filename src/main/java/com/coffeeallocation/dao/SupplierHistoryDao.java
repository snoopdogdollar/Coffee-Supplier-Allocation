package com.coffeeallocation.dao;

import com.coffeeallocation.model.SupplierHistory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class SupplierHistoryDao {
    private final Connection connection;

    public SupplierHistoryDao(Connection connection) {
        this.connection = connection;
    }

    public SupplierHistory.Cutoff currentCutoff() throws SQLException {
        String sql = """
                SELECT LOCALTIMESTAMP AS cutoff,
                       current_setting('TimeZone') AS time_zone
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SQLException("Database did not return a review cutoff.");
            }
            return new SupplierHistory.Cutoff(
                    rows.getObject("cutoff", LocalDateTime.class),
                    rows.getString("time_zone")
            );
        }
    }

    public List<SupplierHistory.Summary> findSummaries(LocalDateTime cutoff)
            throws SQLException {
        String sql = """
                WITH first_reviews AS (
                    SELECT DISTINCT ON (r.order_id)
                           r.order_id, r.supplier_id, r.quality_ok,
                           r.quantity_ok, r.on_time_ok, r.created_at
                    FROM supplier_reviews r
                    JOIN orders o
                      ON o.order_id = r.order_id
                     AND o.supplier_id = r.supplier_id
                    WHERE o.status = 'COMPLETED'
                      AND r.created_at <= ?
                      AND r.created_at >= o.created_at
                    ORDER BY r.order_id, r.created_at, r.review_id
                )
                SELECT s.supplier_id, s.name AS supplier_name, s.is_active,
                       COUNT(r.order_id) AS review_count,
                       COUNT(r.order_id) FILTER (WHERE r.quality_ok)
                           AS quality_ok_count,
                       COUNT(r.order_id) FILTER (WHERE r.quantity_ok)
                           AS quantity_ok_count,
                       COUNT(r.order_id) FILTER (WHERE r.on_time_ok)
                           AS on_time_ok_count,
                       MAX(r.created_at) AS latest_review_at
                FROM suppliers s
                LEFT JOIN first_reviews r ON r.supplier_id = s.supplier_id
                GROUP BY s.supplier_id, s.name, s.is_active
                ORDER BY s.supplier_id
                """;
        List<SupplierHistory.Summary> summaries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, cutoff);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    summaries.add(new SupplierHistory.Summary(
                            rows.getLong("supplier_id"),
                            rows.getString("supplier_name"),
                            rows.getLong("review_count"),
                            rows.getLong("quality_ok_count"),
                            rows.getLong("quantity_ok_count"),
                            rows.getLong("on_time_ok_count"),
                            rows.getObject("latest_review_at", LocalDateTime.class),
                            rows.getBoolean("is_active")
                    ));
                }
            }
        }
        return List.copyOf(summaries);
    }
}
