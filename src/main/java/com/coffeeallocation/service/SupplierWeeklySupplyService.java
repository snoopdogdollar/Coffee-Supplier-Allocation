package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.SupplierSupplyDao;
import com.coffeeallocation.model.SupplierSupplyView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

public final class SupplierWeeklySupplyService {
    private final long supplierId;

    public SupplierWeeklySupplyService(long supplierId) {
        if (supplierId <= 0) {
            throw new IllegalArgumentException("Supplier ID must be positive.");
        }
        this.supplierId = supplierId;
    }

    public SupplierSupplyView.Snapshot load(LocalDate date) throws SQLException {
        Week week = week(date);
        return transaction(true, dao -> new SupplierSupplyView.Snapshot(
                dao.catalog(supplierId), dao.findWeek(supplierId, week.year(), week.number())));
    }

    public long save(Long supplyId, long supplierProductId, LocalDate date,
                     BigDecimal initialQuantity, BigDecimal unitPrice) throws SQLException {
        if (supplierProductId <= 0 || (supplyId != null && supplyId <= 0)) {
            throw new IllegalArgumentException("Product and supply IDs must be positive.");
        }
        Week week = week(date);
        BigDecimal quantity = amount(initialQuantity, "Initial quantity");
        BigDecimal price = amount(unitPrice, "Unit price");
        return transaction(false, dao -> {
            dao.lockActiveProduct(supplierId, supplierProductId);
            if (supplyId == null) {
                return dao.insert(supplierProductId, week.year(), week.number(), quantity, price);
            }
            var current = dao.lockSupply(supplierId, supplyId);
            if (current.supplierProductId() != supplierProductId
                    || current.year() != week.year() || current.week() != week.number()) {
                throw new IllegalArgumentException("An existing supply record must keep its product and week.");
            }
            BigDecimal committed = current.initialQuantity().subtract(current.remainingQuantity());
            if (quantity.compareTo(committed) < 0) {
                throw new IllegalArgumentException("Initial quantity must be at least the committed quantity: "
                        + committed.stripTrailingZeros().toPlainString() + " " + current.unit() + ".");
            }
            dao.update(supplyId, quantity, quantity.subtract(committed), price);
            return supplyId;
        });
    }

    public void delete(long supplyId) throws SQLException {
        if (supplyId <= 0) {
            throw new IllegalArgumentException("Supply ID must be positive.");
        }
        transaction(false, dao -> {
            dao.lockActiveSupplier(supplierId);
            var current = dao.lockSupply(supplierId, supplyId);
            if (current.initialQuantity().compareTo(current.remainingQuantity()) != 0
                    || dao.hasAllocations(supplyId)) {
                throw new IllegalArgumentException("Supply with committed quantity or allocation history cannot be deleted.");
            }
            dao.delete(supplyId);
            return null;
        });
    }

    private static BigDecimal amount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be zero or greater.");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " must have at most two decimal places.");
        }
        if (normalized.precision() > 15) {
            throw new IllegalArgumentException(field + " exceeds the supported decimal range.");
        }
        return normalized;
    }

    private static Week week(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Choose a date in the supply week.");
        }
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("The ISO year must be between 1 and 9999.");
        }
        return new Week(year, date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private record Week(int year, int number) {}

    @FunctionalInterface
    private interface Operation<T> {
        T run(SupplierSupplyDao dao) throws SQLException;
    }

    private <T> T transaction(boolean readOnly, Operation<T> operation) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(readOnly);
            connection.setTransactionIsolation(readOnly
                    ? Connection.TRANSACTION_REPEATABLE_READ : Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = operation.run(new SupplierSupplyDao(connection));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }
}
