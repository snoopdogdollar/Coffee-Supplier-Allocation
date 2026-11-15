package com.coffeeallocation.dao;

import com.coffeeallocation.model.SupplierOverviewView.Totals;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

public final class SupplierOverviewDao {
    private final Connection connection;
    public SupplierOverviewDao(Connection connection){this.connection=connection;}
    public LocalDate databaseDate() throws SQLException {
        try(var statement=connection.prepareStatement("SELECT CURRENT_DATE");var row=statement.executeQuery()) {
            if(!row.next())throw new SQLException("Database date was not returned.");
            return row.getObject(1,LocalDate.class);
        }
    }
    public Totals totals(long supplierId) throws SQLException {
        try(var statement=connection.prepareStatement("""
                SELECT COUNT(*) AS orders,
                       COUNT(*) FILTER (WHERE status='PENDING') AS pending,
                       COUNT(*) FILTER (WHERE status='COMPLETED') AS completed
                FROM orders WHERE supplier_id=?
                """)) {
            statement.setLong(1,supplierId);
            try(var row=statement.executeQuery()) {
                if(!row.next())throw new SQLException("Supplier totals were not returned.");
                return new Totals(row.getLong("orders"),row.getLong("pending"),row.getLong("completed"));
            }
        }
    }
}
