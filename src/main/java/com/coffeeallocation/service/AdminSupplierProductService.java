package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.AdminSupplierProductDao;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.model.AdminSupplierProductView.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class AdminSupplierProductService {
    public Snapshot load(String supplier, String product) throws SQLException {
        String supplierSearch = search(supplier), productSearch = search(product);
        return read(connection -> {
            var dao = new AdminSupplierProductDao(connection);
            return new Snapshot(page(dao.page(supplierSearch, productSearch, null)), dao.activeSuppliers(),
                    new CoffeeProductDao(connection).findActive());
        });
    }
    public Page page(String supplier, String product, Long beforeId) throws SQLException {
        String supplierSearch = search(supplier), productSearch = search(product);
        if (beforeId != null && beforeId <= 0) throw new IllegalArgumentException("Offering page cursor must be positive.");
        return read(connection -> page(new AdminSupplierProductDao(connection).page(supplierSearch, productSearch, beforeId)));
    }
    public boolean offer(long supplierId, long productId) throws SQLException {
        return new SupplierProductService(supplierId).add(productId);
    }
    public void stop(long supplierId, long offeringId) throws SQLException {
        new SupplierProductService(supplierId).stopOffering(offeringId);
    }
    private static Page page(List<Row> rows) {
        var visible = rows.subList(0, Math.min(20, rows.size()));
        return new Page(visible, rows.size() > 20 ? visible.getLast().offeringId() : null);
    }
    private static String search(String input) {
        String value = input == null ? "" : input.strip();
        if (value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > 100) {
            throw new IllegalArgumentException("Filters must contain at most 100 characters and no unsupported characters.");
        }
        return value;
    }
    @FunctionalInterface private interface Read<T> { T run(Connection connection) throws SQLException; }
    private <T> T read(Read<T> action) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                T result = action.run(connection);connection.commit();return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
}
