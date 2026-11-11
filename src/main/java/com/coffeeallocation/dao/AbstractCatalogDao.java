package com.coffeeallocation.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shares the active-catalog query workflow while subclasses supply SQL and mapping.
 * The caller owns the connection and its transaction; only query resources are closed here.
 *
 * @param <T> the catalog object created from each result row
 */
public abstract class AbstractCatalogDao<T> {
    private final Connection connection;

    protected AbstractCatalogDao(Connection connection) {
        this.connection = connection;
    }

    protected final Connection connection() {
        return connection;
    }

    protected abstract String activeQuery();

    protected abstract T mapRow(ResultSet row) throws SQLException;

    public List<T> findActive() throws SQLException {
        List<T> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(activeQuery())) {
            statement.setBoolean(1, true);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(mapRow(resultSet));
                }
            }
        }
        return entries;
    }
}
