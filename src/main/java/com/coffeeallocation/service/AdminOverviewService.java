package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.AdminOverviewDao;
import com.coffeeallocation.dao.SupplierHistoryDao;
import com.coffeeallocation.model.AdminOverviewView;
import java.sql.Connection;
import java.sql.SQLException;

public final class AdminOverviewService {
    public AdminOverviewView.Snapshot load() throws SQLException {
        try(var connection=DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
            try {
                var dao=new AdminOverviewDao(connection);
                var historyDao=new SupplierHistoryDao(connection);
                var cutoff=historyDao.currentCutoff();
                var rankings=new SupplierRankingService().rank(historyDao.findSummaries(cutoff.timestamp()));
                var result=new AdminOverviewView.Snapshot(dao.totals(),dao.recentOrders(),rankings,cutoff);
                connection.commit();return result;
            } catch(SQLException|RuntimeException exception) {
                try {connection.rollback();}catch(SQLException rollback){exception.addSuppressed(rollback);}throw exception;
            }
        }
    }
}
