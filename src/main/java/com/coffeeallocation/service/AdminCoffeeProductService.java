package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductCatalogDao;
import com.coffeeallocation.model.CoffeeProductDetails;
import java.sql.SQLException;
import java.util.List;

public final class AdminCoffeeProductService {
    public List<CoffeeProductDetails> load() throws SQLException {
        return transaction(true, CoffeeProductCatalogDao::findAll);
    }

    public long add(String name, String description, String unit, String variety) throws SQLException {
        String validName = field(name, "Name", 100, true);
        String validDescription = field(description, "Description", 255, false);
        String validUnit = field(unit, "Unit", 20, true);
        String validVariety = field(variety, "Variety", 50, false);
        return transaction(false, dao -> dao.insert(validName, validDescription, validUnit, validVariety));
    }

    /** expectedActive prevents a stale selection from reversing another admin's change. */
    public void changeStatus(long productId, boolean expectedActive) throws SQLException {
        if (productId <= 0) throw new IllegalArgumentException("Choose a valid coffee product.");
        transaction(false, dao -> { dao.changeStatus(productId, expectedActive); return null; });
    }

    private static String field(String input, String name, int maxLength, boolean required) {
        String value = input == null ? "" : input.strip();
        if (required && value.isBlank()) throw new IllegalArgumentException(name + " is required.");
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException(name + " contains an unsupported character.");
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(name + " must contain at most " + maxLength + " characters.");
        }
        return value.isEmpty() && !required ? null : value;
    }

    @FunctionalInterface
    private interface Operation<T> { T run(CoffeeProductCatalogDao dao) throws SQLException; }

    private <T> T transaction(boolean readOnly, Operation<T> operation) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(readOnly);
            connection.setAutoCommit(false);
            try {
                T result = operation.run(new CoffeeProductCatalogDao(connection));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); }
                catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
}
