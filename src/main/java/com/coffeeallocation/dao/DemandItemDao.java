package com.coffeeallocation.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DemandItemDao {

    private final Connection connection;

    public DemandItemDao(Connection connection) {
        this.connection = connection;
    }

    public long insertItem(
            long demandId,
            long productId,
            BigDecimal quantity,
            String note) throws SQLException {

        if (connection.getAutoCommit()) {
            throw new SQLException(
                "Service must disable auto-commit before inserting an item."
            );
        }

        String sql = """
                INSERT INTO demand_items (
                    demand_id,
                    product_id,
                    quantity,
                    note
                )
                VALUES (?, ?, ?, ?)
                RETURNING demand_item_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, demandId);
            statement.setLong(2, productId);
            statement.setBigDecimal(3, quantity);
            statement.setString(4, note);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                        "Insert did not return a demand item ID."
                    );
                }

                return resultSet.getLong("demand_item_id");
            }
        }
    }
}