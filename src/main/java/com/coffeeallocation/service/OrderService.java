package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.OrderQueryDao;
import com.coffeeallocation.model.OrderView;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class OrderService {

    public List<OrderView.Summary> findPage(Long beforeId)
            throws SQLException {

        if (beforeId != null && beforeId <= 0) {
            throw new IllegalArgumentException("Invalid order cursor.");
        }

        return read(dao -> dao.findPage(beforeId));
    }

    public Optional<OrderView.Details> findDetails(long orderId)
            throws SQLException {

        if (orderId <= 0) {
            throw new IllegalArgumentException(
                    "Order ID must be positive."
            );
        }

        return read(dao -> {
            var summary = dao.findSummary(orderId);

            if (summary.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new OrderView.Details(
                    summary.get(),
                    dao.findItems(orderId)
            ));
        });
    }

    @FunctionalInterface
    private interface ReadOperation<T> {
        T run(OrderQueryDao dao) throws SQLException;
    }

    private <T> T read(ReadOperation<T> operation)
            throws SQLException {

        try (Connection connection =
                DatabaseConnection.getConnection()) {

            connection.setReadOnly(true);
            connection.setTransactionIsolation(
                    Connection.TRANSACTION_REPEATABLE_READ
            );
            connection.setAutoCommit(false);

            try {
                T result = operation.run(
                        new OrderQueryDao(connection)
                );

                connection.commit();
                return result;
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
}