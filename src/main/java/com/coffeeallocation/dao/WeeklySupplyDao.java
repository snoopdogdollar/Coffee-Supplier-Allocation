package com.coffeeallocation.dao;

import com.coffeeallocation.model.AvailableSupply;
import java.sql.Connection;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WeeklySupplyDao {

    private final Connection connection;

    public WeeklySupplyDao(Connection connection) {
        this.connection = connection;
    }

    public List<AvailableSupply> findEligible(
            long productId, int year, int week) throws SQLException {

        String sql = """
                SELECT sw.supply_id,
                       s.supplier_id,
                       s.name AS supplier_name,
                       sw.remaining_quantity,
                       sw.unit_price
                FROM supplier_weekly_supply sw
                JOIN supplier_products sp
                    ON sp.supplier_product_id = sw.supplier_product_id
                JOIN suppliers s
                    ON s.supplier_id = sp.supplier_id
                JOIN coffee_products p
                    ON p.product_id = sp.product_id
                WHERE sp.product_id = ?
                  AND sw.year = ?
                  AND sw.week = ?
                  AND p.is_active = TRUE
                  AND sp.is_active = TRUE
                  AND s.is_active = TRUE
                  AND sw.remaining_quantity > 0
                ORDER BY s.name, sw.supply_id
                """;

        List<AvailableSupply> supplies = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, productId);
            statement.setInt(2, year);
            statement.setInt(3, week);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    supplies.add(new AvailableSupply(
                        resultSet.getLong("supply_id"),
                        resultSet.getLong("supplier_id"),
                        resultSet.getString("supplier_name"),
                        resultSet.getBigDecimal("remaining_quantity"),
                        resultSet.getBigDecimal("unit_price")
                    ));
                }
            }
        }

        return supplies;
    }
    
    public void deductQuantity(long supplyId, BigDecimal quantity)
        throws SQLException {

    if (supplyId <= 0
            || quantity == null
            || quantity.signum() <= 0
            || quantity.stripTrailingZeros().scale() > 2) {
        throw new IllegalArgumentException(
                "Invalid supply ID or quantity."
        );
    }

    if (connection.getAutoCommit()) {
        throw new SQLException(
                "Service must start a transaction before deducting supply."
        );
    }

    String sql = """
            UPDATE supplier_weekly_supply
            SET remaining_quantity = remaining_quantity - ?
            WHERE supply_id = ?
              AND remaining_quantity >= ?
            """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setBigDecimal(1, quantity);
        statement.setLong(2, supplyId);
        statement.setBigDecimal(3, quantity);

        int updatedRows = statement.executeUpdate();

        if (updatedRows != 1) {
            throw new SQLException(
                    "Supply does not exist or has insufficient quantity."
            );
        }
    }
}
    public void lockSuppliesForWeek(int year, int week)
        throws SQLException {

    if (connection.getAutoCommit()) {
        throw new SQLException(
                "Service must start a transaction before locking supplies."
        );
    }

    String sql = """
            SELECT supply_id
            FROM supplier_weekly_supply
            WHERE year = ?
              AND week = ?
            ORDER BY supply_id
            FOR UPDATE
            """;

    try (PreparedStatement statement =
            connection.prepareStatement(sql)) {

        statement.setInt(1, year);
        statement.setInt(2, week);

        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                // Read every row to acquire all supply locks.
            }
        }
    }
}
}