package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.dao.WeeklySupplyDao;
import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.DemandSupplyMatch;
import com.coffeeallocation.model.OpenDemandItem;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

public final class MatchingService {

    public DemandSupplyMatch findForItem(
            long demandItemId, int year, int week) throws SQLException {
        if (demandItemId <= 0) {
            throw new IllegalArgumentException("Demand item ID must be positive.");
        }
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("ISO year must be between 1 and 9999.");
        }
        int lastWeek = LocalDate.of(year, 12, 28)
                .get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) {
            throw new IllegalArgumentException("Invalid ISO week for year " + year + ".");
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            // Both queries see the same snapshot. This does not reserve supply.
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);

            try {
                List<OpenDemandItem> items = new DemandDao(connection)
                        .findOpenItemsForWeek(year, week);
                OpenDemandItem selected = null;
                for (OpenDemandItem item : items) {
                    if (item.getDemandItemId() == demandItemId) {
                        selected = item;
                        break;
                    }
                }
                if (selected == null) {
                    throw new IllegalArgumentException(
                            "Demand item is not available as OPEN in this week.");
                }

                List<AvailableSupply> supplies = new WeeklySupplyDao(connection)
                        .findEligible(selected.getProductId(), year, week);
                DemandSupplyMatch match = new DemandSupplyMatch(selected, supplies);
                connection.commit();
                return match;
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
