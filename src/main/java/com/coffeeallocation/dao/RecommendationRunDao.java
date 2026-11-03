package com.coffeeallocation.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RecommendationRunDao {

    private final Connection connection;

    public RecommendationRunDao(Connection connection) {
        this.connection = connection;
    }

    public long insert(
            long demandItemId,
            String algorithmVersion,
            String status) throws SQLException {

        if (demandItemId <= 0) {
            throw new IllegalArgumentException(
                    "Demand item ID must be positive."
            );
        }

        if (algorithmVersion == null
                || algorithmVersion.isBlank()
                || algorithmVersion.length() > 50) {
            throw new IllegalArgumentException(
                    "Invalid algorithm version."
            );
        }

        if (status == null
                || status.isBlank()
                || status.length() > 20) {
            throw new IllegalArgumentException(
                    "Invalid run status."
            );
        }

        if (connection.getAutoCommit()) {
            throw new SQLException(
                    "Service must start a transaction before inserting a run."
            );
        }

        String sql = """
                INSERT INTO recommendation_runs (
                    demand_item_id,
                    algorithm_version,
                    status
                )
                VALUES (?, ?, ?)
                RETURNING run_id
                """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setLong(1, demandItemId);
            statement.setString(2, algorithmVersion);
            statement.setString(3, status);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "Insert did not return a run ID."
                    );
                }

                return resultSet.getLong("run_id");
            }
        }
    }
}