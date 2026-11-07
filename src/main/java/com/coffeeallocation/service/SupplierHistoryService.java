package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.SupplierHistoryDao;
import com.coffeeallocation.model.SupplierHistory;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class SupplierHistoryService {
    public SupplierHistory.Report findReport(LocalDateTime requestedCutoff)
            throws SQLException {
        if (requestedCutoff != null && requestedCutoff.getYear() < 1) {
            throw new IllegalArgumentException("Choose a cutoff in year 1 or later.");
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                SupplierHistoryDao dao = new SupplierHistoryDao(connection);
                var databaseTime = dao.currentCutoff();
                LocalDateTime cutoff = requestedCutoff == null
                        ? databaseTime.timestamp()
                        : requestedCutoff.truncatedTo(ChronoUnit.MICROS);
                if (cutoff.isAfter(databaseTime.timestamp())) {
                    throw new IllegalArgumentException(
                            "Choose a cutoff at or before the current database time."
                    );
                }
                var report = new SupplierHistory.Report(
                        new SupplierHistory.Cutoff(cutoff, databaseTime.timeZone()),
                        dao.findSummaries(cutoff)
                );
                connection.commit();
                return report;
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
