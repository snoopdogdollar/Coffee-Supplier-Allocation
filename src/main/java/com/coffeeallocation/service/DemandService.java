package com.coffeeallocation.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.CoffeeShopDao;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.dao.DemandItemDao;
import com.coffeeallocation.model.NewDemandItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class DemandService {

    // OPEN is the existing demo label, not a complete production state machine.
    private static final String DEMO_OPEN_STATUS = "OPEN";

    public long createOpenDemand(
            long shopId,
            LocalDate requestDate,
            int year,
            int week,
            String note,
            List<NewDemandItem> items) throws SQLException {

        validateHeader(shopId, requestDate, year, week, note);
        if (items == null) {
            throw new IllegalArgumentException("A demand must contain at least one item.");
        }

        // Snapshot the list before opening a transaction. Each item is immutable.
        List<NewDemandItem> requestItems = new ArrayList<>(items);
        if (requestItems.isEmpty()) {
            throw new IllegalArgumentException("A demand must contain at least one item.");
        }
        for (NewDemandItem item : requestItems) {
            if (item == null) {
                throw new IllegalArgumentException("Demand items must not contain null.");
            }
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);

            try {
                // Schema timestamps have no timezone; use the seed's UTC convention.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SET LOCAL TIME ZONE 'UTC'")) {
                    statement.execute();
                }

                CoffeeShopDao shopDao = new CoffeeShopDao(connection);
                CoffeeProductDao productDao = new CoffeeProductDao(connection);
                if (!shopDao.lockActiveById(shopId)) {
                    throw new IllegalArgumentException("Shop does not exist or is inactive.");
                }

                // Only lock IDs are deduplicated/sorted; every requested line is kept.
                TreeSet<Long> productIds = new TreeSet<>();
                for (NewDemandItem item : requestItems) {
                    productIds.add(item.getProductId());
                }
                for (long productId : productIds) {
                    if (!productDao.lockActiveById(productId)) {
                        throw new IllegalArgumentException(
                            "Product does not exist or is inactive: " + productId
                        );
                    }
                }

                DemandDao demandDao = new DemandDao(connection);
                DemandItemDao itemDao = new DemandItemDao(connection);
                long demandId = demandDao.insertDemand(
                    shopId, requestDate, year, week, DEMO_OPEN_STATUS, note
                );

                for (NewDemandItem item : requestItems) {
                    itemDao.insertItem(
                        demandId, item.getProductId(), item.getQuantity(), item.getNote()
                    );
                }

                connection.commit();
                return demandId;
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

    private static void validateHeader(
            long shopId, LocalDate requestDate, int year, int week, String note) {

        if (shopId <= 0) {
            throw new IllegalArgumentException("Shop ID must be positive.");
        }
        if (requestDate == null) {
            throw new IllegalArgumentException("Request date is required.");
        }
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("Required ISO year must be between 1 and 9999.");
        }

        int lastWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) {
            throw new IllegalArgumentException("Invalid ISO week for year " + year + ".");
        }
        if (note != null && note.codePointCount(0, note.length()) > 255) {
            throw new IllegalArgumentException("Demand note must be at most 255 characters.");
        }
    }
}
