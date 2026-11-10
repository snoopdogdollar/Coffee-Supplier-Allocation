package com.coffeemongers.service;

import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.CoffeeProductDao;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.model.NewDemandItem;
import com.coffeeallocation.service.DemandService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;

/** Persistent buyer catalog and demands for one configured coffee shop. */
public final class BuyerDemandService {
    private final long shopId;
    private final DemandService demandService = new DemandService();

    public record Product(long id, String name, String unit) { }

    public record Item(long productId, BigDecimal quantity, String note) { }

    public record SavedDemand(long id, LocalDate requestDate, int requiredYear,
                              int requiredWeek, String status, String note,
                              List<SavedItem> items) {
        public SavedDemand {
            items = List.copyOf(items);
        }
    }

    public record SavedItem(long productId, String productName, String unit,
                            BigDecimal quantity, String note) { }

    public BuyerDemandService(long shopId) {
        if (shopId <= 0) {
            throw new IllegalArgumentException("Shop ID must be positive.");
        }
        this.shopId = shopId;
    }

    public List<Product> products() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            return new CoffeeProductDao(connection).findActive().stream()
                    .map(product -> new Product(product.getProductId(),
                            product.getName(), product.getUnit()))
                    .toList();
        }
    }

    public long submit(LocalDate requiredDeliveryDate, String note, List<Item> items)
            throws SQLException {
        LocalDate requestDate = LocalDate.now();
        if (requiredDeliveryDate == null || requiredDeliveryDate.isBefore(requestDate)) {
            throw new IllegalArgumentException("Required delivery date must be today or later.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A demand must contain at least one item.");
        }

        List<NewDemandItem> requestItems = items.stream().map(item -> {
            if (item == null) {
                throw new IllegalArgumentException("Demand items must not contain null.");
            }
            return new NewDemandItem(item.productId(), item.quantity(), item.note());
        }).toList();
        return demandService.createOpenDemand(shopId, requestDate,
                requiredDeliveryDate.get(IsoFields.WEEK_BASED_YEAR),
                requiredDeliveryDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                note, requestItems);
    }

    public List<SavedDemand> recentDemands() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setReadOnly(true);
            return new DemandDao(connection).findRecentByShop(shopId).stream()
                    .map(demand -> new SavedDemand(demand.id(), demand.requestDate(),
                            demand.requiredYear(), demand.requiredWeek(), demand.status(),
                            demand.note(), demand.items().stream()
                                    .map(item -> new SavedItem(item.productId(), item.productName(),
                                            item.unit(), item.quantity(), item.note()))
                                    .toList()))
                    .toList();
        }
    }
}
