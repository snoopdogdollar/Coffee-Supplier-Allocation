package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.SupplierReviewDao;
import com.coffeeallocation.model.SupplierReview;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class SupplierReviewService {
    public List<SupplierReview> findByOrder(long shopId, long orderId)
            throws SQLException {
        validateIds(shopId, orderId);
        try (Connection connection = DatabaseConnection.getConnection()) {
            return new SupplierReviewDao(connection).findByOrder(shopId, orderId);
        }
    }

    public long submit(long shopId, long orderId,
            boolean qualityOk, boolean quantityOk, boolean onTimeOk,
            String comment) throws SQLException {
        validateIds(shopId, orderId);
        String normalizedComment = comment == null ? null : comment.strip();
        if (normalizedComment != null) {
            if (normalizedComment.codePointCount(
                    0, normalizedComment.length()) > 255) {
                throw new IllegalArgumentException(
                        "Comment must contain at most 255 characters."
                );
            }
            if (normalizedComment.isEmpty()) {
                normalizedComment = null;
            }
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                SupplierReviewDao dao = new SupplierReviewDao(connection);
                var supplierId = dao.lockCompletedOrder(shopId, orderId);
                if (supplierId.isEmpty()) {
                    throw new IllegalArgumentException(
                            "A COMPLETED order belonging to this shop is required."
                    );
                }
                if (!dao.findByOrder(shopId, orderId).isEmpty()) {
                    throw new IllegalArgumentException(
                            "This order already has a review."
                    );
                }

                long reviewId = dao.insert(
                        orderId, supplierId.getAsLong(),
                        qualityOk, quantityOk, onTimeOk, normalizedComment
                );
                connection.commit();
                return reviewId;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    private static void validateIds(long shopId, long orderId) {
        if (shopId <= 0 || orderId <= 0) {
            throw new IllegalArgumentException(
                    "Shop ID and order ID must be positive."
            );
        }
    }
}
