package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.SupplierSupplyDao;
import com.coffeeallocation.model.CoffeeProduct;
import com.coffeeallocation.model.SupplierSupplyView;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Manages a single supplier's offerings without deleting supply or order history. */
public final class SupplierProductService {
    private final long supplierId;

    public record Catalog(String supplierName, List<CoffeeProduct> available,
                          List<SupplierSupplyView.Product> offered) {
        public Catalog {
            available = List.copyOf(available);
            offered = List.copyOf(offered);
        }
    }

    public SupplierProductService(long supplierId) {
        if (supplierId <= 0) throw new IllegalArgumentException("Supplier ID must be positive.");
        this.supplierId = supplierId;
    }

    public Catalog load() throws SQLException {
        return transaction(true, connection -> {
            var own = new SupplierSupplyDao(connection).catalog(supplierId);
            return new Catalog(own.supplierName(), new CoffeeProductDao(connection).findActive(), own.products());
        });
    }

    public boolean add(long productId) throws SQLException {
        if (productId <= 0) throw new IllegalArgumentException("Choose an active coffee product.");
        return transaction(false, connection -> {
            var dao = new SupplierSupplyDao(connection);
            dao.lockActiveSupplier(supplierId);
            if (!new CoffeeProductDao(connection).lockActiveById(productId)) {
                throw new IllegalArgumentException("This coffee product does not exist or is inactive.");
            }
            return dao.offerProduct(supplierId, productId);
        });
    }

    public void stopOffering(long supplierProductId) throws SQLException {
        if (supplierProductId <= 0) throw new IllegalArgumentException("Choose a listed supplier product.");
        transaction(false, connection -> {
            var dao = new SupplierSupplyDao(connection);
            dao.lockActiveSupplier(supplierId);
            dao.stopOffering(supplierId, supplierProductId);
            return null;
        });
    }

    @FunctionalInterface
    private interface Operation<T> { T run(Connection connection) throws SQLException; }

    private <T> T transaction(boolean readOnly, Operation<T> operation) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(readOnly);
            connection.setTransactionIsolation(readOnly
                    ? Connection.TRANSACTION_REPEATABLE_READ : Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = operation.run(connection);
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
