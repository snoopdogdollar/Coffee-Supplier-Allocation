package com.coffeeallocation.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AllocationDao {

    private final Connection connection;

    public AllocationDao(Connection connection) {
        this.connection = connection;
    }

    public long insert(
            long demandItemId,
            long supplierId,
            long runId,
            long supplyId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String status) throws SQLException {

        if (demandItemId <= 0
                || supplierId <= 0
                || runId <= 0
                || supplyId <= 0) {
            throw new IllegalArgumentException(
                    "Allocation IDs must be positive."
            );
        }

        validateDecimal(quantity, false, "quantity");
        validateDecimal(unitPrice, true, "unit price");

        if (status == null
                || status.isBlank()
                || status.length() > 20) {
            throw new IllegalArgumentException(
                    "Invalid allocation status."
            );
        }

        if (connection.getAutoCommit()) {
            throw new SQLException(
                    "Service must start a transaction before inserting an allocation."
            );
        }

        String sql = """
                INSERT INTO allocations (
                    demand_item_id,
                    supplier_id,
                    run_id,
                    supply_id,
                    allocated_quantity,
                    allocated_price,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING allocation_id
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setLong(1, demandItemId);
            statement.setLong(2, supplierId);
            statement.setLong(3, runId);
            statement.setLong(4, supplyId);
            statement.setBigDecimal(5, quantity);
            statement.setBigDecimal(6, unitPrice);
            statement.setString(7, status);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "Insert did not return an allocation ID."
                    );
                }

                return resultSet.getLong("allocation_id");
            }
        }
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