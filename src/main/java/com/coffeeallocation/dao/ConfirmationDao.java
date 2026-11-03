package com.coffeeallocation.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class ConfirmationDao {
    private final Connection connection;

    public ConfirmationDao(Connection connection) {
        this.connection = connection;
    }

    public boolean markDemandAllocatedIfComplete(long demandId) throws SQLException {
        if (connection.getAutoCommit()) {
            throw new SQLException("Service must start a transaction first.");
        }

        String sql = """
                UPDATE demands d
                SET status = 'ALLOCATED'
                WHERE d.demand_id = ?
                  AND d.status = 'OPEN'
                  AND EXISTS (
                      SELECT 1 FROM demand_items di
                      WHERE di.demand_id = d.demand_id
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM demand_items di
                      WHERE di.demand_id = d.demand_id
                        AND di.quantity <> COALESCE((
                            SELECT SUM(a.allocated_quantity)
                            FROM allocations a
                            WHERE a.demand_item_id = di.demand_item_id
                              AND a.status = 'CONFIRMED'
                        ), 0)
                  )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, demandId);
            return statement.executeUpdate() == 1;
        }
    }
}
