package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.AdminActivityDao;
import com.coffeeallocation.model.AdminActivityView.*;
import com.coffeeallocation.model.OrderView;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

public final class AdminActivityService {
    public Snapshot load(String query) throws SQLException {
        String search = search(query);
        return read(dao -> new Snapshot(page(dao.orders(search, null), Order::id),
                page(dao.demands(search, null), Demand::id)));
    }
    public Page<Order> orders(String query, Long beforeId) throws SQLException {
        String search = search(query); cursor(beforeId);
        return read(dao -> page(dao.orders(search, beforeId), Order::id));
    }
    public Page<Demand> demands(String query, Long beforeId) throws SQLException {
        String search = search(query); cursor(beforeId);
        return read(dao -> page(dao.demands(search, beforeId), Demand::id));
    }
    public Optional<OrderView.Details> orderDetails(long id) throws SQLException {
        cursor(id);
        return new OrderService().findDetails(id);
    }
    public Optional<DemandDetails> demandDetails(long id) throws SQLException {
        cursor(id);
        return read(dao -> {
            var summary = dao.findDemand(id);
            return summary.isEmpty() ? Optional.empty()
                    : Optional.of(new DemandDetails(summary.get(), dao.demandItems(id)));
        });
    }
    private static String search(String query) {
        String value = query == null ? "" : query.strip();
        if (value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > 100) {
            throw new IllegalArgumentException("Search must contain at most 100 characters and no unsupported characters.");
        }
        return value;
    }
    private static void cursor(Long id) {
        if (id != null && id <= 0) throw new IllegalArgumentException("Record IDs and page cursors must be positive.");
    }
    private static <T> Page<T> page(List<T> rows, ToLongFunction<T> id) {
        List<T> visible = rows.subList(0, Math.min(20, rows.size()));
        return new Page<>(visible, rows.size() > 20 ? id.applyAsLong(visible.getLast()) : null);
    }
    @FunctionalInterface private interface Read<T> { T run(AdminActivityDao dao) throws SQLException; }
    private <T> T read(Read<T> action) throws SQLException {
        try (var connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                T result = action.run(new AdminActivityDao(connection));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            }
        }
    }
}
