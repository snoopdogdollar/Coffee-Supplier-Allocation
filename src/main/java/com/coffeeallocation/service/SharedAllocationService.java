// read demand and supply data from the database, and use the SharedSupplyAllocator 
// to preview the allocation for a given year and week. 
// This service handles database connections, transactions, and error handling.
package com.coffeeallocation.service;

import com.coffeeallocation.algorithm.SharedSupplyAllocator;
import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.dao.WeeklySupplyDao;
import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.model.OpenDemandItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class SharedAllocationService {
    public BatchAllocationPreview preview(int year, int week) throws SQLException {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("Invalid ISO year.");
        }
        int lastWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) {
            throw new IllegalArgumentException("Invalid ISO week for this year.");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                List<OpenDemandItem> items = new DemandDao(connection)
                        .findOpenUnallocatedItemsForWeek(year, week);
                TreeSet<Long> productIds = new TreeSet<>();
                for (OpenDemandItem item : items) {
                    productIds.add(item.getProductId());
                }

                WeeklySupplyDao supplyDao = new WeeklySupplyDao(connection);
                Map<Long, List<AvailableSupply>> sources = new LinkedHashMap<>();
                for (long productId : productIds) {
                    sources.put(productId, supplyDao.findEligible(productId, year, week));
                }

                BatchAllocationPreview result = SharedSupplyAllocator.preview(items, sources);
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
