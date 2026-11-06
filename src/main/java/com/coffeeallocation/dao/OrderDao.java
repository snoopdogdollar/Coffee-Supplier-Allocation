package com.coffeeallocation.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class OrderDao {

    private final Connection connection;

    public OrderDao(Connection connection) {
        this.connection = connection;
    }

    public boolean completePendingOrder(long shopId, long orderId)
            throws SQLException {
        String sql = """
                UPDATE orders
                SET status = 'COMPLETED'
                WHERE shop_id = ? AND order_id = ? AND status = 'PENDING'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, shopId);
            statement.setLong(2, orderId);
            return statement.executeUpdate() == 1;
        }
    }

    public long insertForAllocation(
            long shopId,
            long supplierId,
            long allocationId,
            long productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String status) throws SQLException {

        if (shopId <= 0
                || supplierId <= 0
                || allocationId <= 0
                || productId <= 0) {
            throw new IllegalArgumentException(
                    "Order IDs must be positive."
            );
        }

        validateDecimal(quantity, false, "quantity");
        validateDecimal(unitPrice, true, "unit price");

        if (status == null
                || status.isBlank()
                || status.length() > 20) {
            throw new IllegalArgumentException(
                    "Invalid order status."
            );
        }

        if (connection.getAutoCommit()) {
            throw new SQLException(
                    "Service must start a transaction before creating an order."
            );
        }

        String orderSql = """
                INSERT INTO orders (
                    shop_id,
                    supplier_id,
                    allocation_id,
                    status
                )
                VALUES (?, ?, ?, ?)
                RETURNING order_id
                """;

        long orderId;

        try (PreparedStatement statement =
                connection.prepareStatement(orderSql)) {

            statement.setLong(1, shopId);
            statement.setLong(2, supplierId);
            statement.setLong(3, allocationId);
            statement.setString(4, status);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "Insert did not return an order ID."
                    );
                }

                orderId = resultSet.getLong("order_id");
            }
        }

        String itemSql = """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    quantity,
                    unit_price
                )
                VALUES (?, ?, ?, ?)
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(itemSql)) {

            statement.setLong(1, orderId);
            statement.setLong(2, productId);
            statement.setBigDecimal(3, quantity);
            statement.setBigDecimal(4, unitPrice);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                        "Order item was not inserted."
                );
            }
        }

        return orderId;
    }

    private static void validateDecimal(
            BigDecimal value,
            boolean allowZero,
            String fieldName) {

        if (value == null
                || value.signum() < 0
                || (!allowZero && value.signum() == 0)
                || value.stripTrailingZeros().scale() > 2
                || value.compareTo(
                        new BigDecimal("9999999999999.99")) > 0) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + "."
            );
        }
    }
}