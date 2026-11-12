package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.AdminSupplyDao;
import com.coffeeallocation.model.AdminSupplyView;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;

public final class AdminSupplyService {
    public AdminSupplyView.Page page(int year, int week, String supplier, String product, Long beforeId)
            throws SQLException {
        if (year < 1 || year > 9999) throw new IllegalArgumentException("ISO year must be between 1 and 9999.");
        int lastWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) throw new IllegalArgumentException("Choose a valid ISO week for this year (1–" + lastWeek + ").");
        return read(year, week, supplier, product, beforeId);
    }
    public AdminSupplyView.Page allWeeks(String supplier, String product, Long beforeId) throws SQLException {
        return read(null, null, supplier, product, beforeId);
    }
    private AdminSupplyView.Page read(Integer year, Integer week, String supplier, String product, Long beforeId)
            throws SQLException {
        if (beforeId != null && beforeId <= 0) throw new IllegalArgumentException("Supply page cursor must be positive.");
        String supplierSearch = search(supplier), productSearch = search(product);
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var rows = new AdminSupplyDao(connection).page(year, week, supplierSearch, productSearch, beforeId);
                var visible = rows.subList(0, Math.min(20, rows.size()));
                var result = new AdminSupplyView.Page(visible, rows.size() > 20 ? visible.getLast().supplyId() : null);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
    private static String search(String query) {
        String value = query == null ? "" : query.strip();
        if (value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > 100) {
            throw new IllegalArgumentException("Filters must contain at most 100 characters and no unsupported characters.");
        }
        return value;
    }
}
