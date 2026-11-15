package com.coffeemongers.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.*;
import com.coffeeallocation.model.SupplierOverviewView;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/** A single read-only snapshot scoped to the configured Supplier account. */
public final class SupplierOverviewService {
    private final long supplierId;
    public SupplierOverviewService(long supplierId){if(supplierId<=0)throw new IllegalArgumentException("Supplier ID must be positive.");this.supplierId=supplierId;}
    public static LocalDate monday(LocalDate date) {
        if(date==null)throw new IllegalArgumentException("Choose a date in the supply week.");
        int year=date.get(IsoFields.WEEK_BASED_YEAR);
        if(year<1||year>9999)throw new IllegalArgumentException("ISO year must be between 1 and 9999.");
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public SupplierOverviewView.Snapshot load(LocalDate requestedWeek)throws SQLException {
        if(requestedWeek!=null)monday(requestedWeek);
        try(var connection=DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
            try {
                var overview=new SupplierOverviewDao(connection);var supply=new SupplierSupplyDao(connection);
                var week=monday(requestedWeek==null?overview.databaseDate():requestedWeek);
                var snapshot=new SupplierOverviewView.Snapshot(supply.catalog(supplierId),week,
                        supply.findWeek(supplierId,week.get(IsoFields.WEEK_BASED_YEAR),week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
                        overview.totals(supplierId),new OrderQueryDao(connection).findSupplierPage(supplierId,null).stream().limit(5).toList());
                connection.commit();return snapshot;
            } catch(SQLException|RuntimeException exception) {
                try{connection.rollback();}catch(SQLException rollback){exception.addSuppressed(rollback);}throw exception;
            }
        }
    }
}
