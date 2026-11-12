package com.coffeeallocation.dao;

import com.coffeeallocation.model.BusinessPartyDetails;
import com.coffeeallocation.model.BusinessPartyType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Both catalog tables share these fields. Identifiers come only from this fixed enum. */
public final class BusinessPartyCatalogDao {
    private final Connection connection;
    private final String table;
    private final String idColumn;

    public BusinessPartyCatalogDao(Connection connection, BusinessPartyType type) {
        this.connection = connection;
        Objects.requireNonNull(type, "Catalog type is required.");
        table = switch (type) { case SHOP -> "coffee_shops"; case SUPPLIER -> "suppliers"; };
        idColumn = switch (type) { case SHOP -> "shop_id"; case SUPPLIER -> "supplier_id"; };
    }

    public List<BusinessPartyDetails> findAll() throws SQLException {
        String sql = "SELECT " + idColumn + " AS id, name, contact_person, phone, email, address, is_active, created_at FROM "
                + table + " ORDER BY name, " + idColumn;
        try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
            List<BusinessPartyDetails> result = new ArrayList<>();
            while (rows.next()) {
                result.add(new BusinessPartyDetails(rows.getLong("id"), rows.getString("name"),
                        rows.getString("contact_person"), rows.getString("phone"), rows.getString("email"),
                        rows.getString("address"), rows.getBoolean("is_active"),
                        rows.getTimestamp("created_at").toLocalDateTime()));
            }
            return List.copyOf(result);
        }
    }

    public long insert(String name, String contactPerson, String phone, String email, String address) throws SQLException {
        requireTransaction();
        String sql = "INSERT INTO " + table
                + " (name, contact_person, phone, email, address) VALUES (?, ?, ?, ?, ?) RETURNING " + idColumn;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, contactPerson);
            statement.setString(3, phone);
            statement.setString(4, email);
            statement.setString(5, address);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("The inserted catalog ID was not returned.");
                return rows.getLong(1);
            }
        }
    }

    public void changeStatus(long id, boolean expectedActive) throws SQLException {
        requireTransaction();
        String sql = "UPDATE " + table + " SET is_active = ? WHERE " + idColumn + " = ? AND is_active = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, !expectedActive);
            statement.setLong(2, id);
            statement.setBoolean(3, expectedActive);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("The record was removed or its status changed. Refresh the catalog before trying again.");
            }
        }
    }

    private void requireTransaction() throws SQLException {
        if (connection.getAutoCommit()) throw new SQLException("Catalog writes require a service transaction.");
    }
}
