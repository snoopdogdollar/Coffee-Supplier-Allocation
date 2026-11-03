package com.coffeeallocation.service;

import com.coffeeallocation.algorithm.SharedSupplyAllocator;
import com.coffeeallocation.config.DatabaseConnection;
import com.coffeeallocation.dao.AllocationDao;
import com.coffeeallocation.dao.ConfirmationDao;
import com.coffeeallocation.dao.DemandDao;
import com.coffeeallocation.dao.OrderDao;
import com.coffeeallocation.dao.RecommendationRunDao;
import com.coffeeallocation.dao.WeeklySupplyDao;
import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.model.BatchItemAllocation;
import com.coffeeallocation.model.OpenDemandItem;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class AllocationConfirmationService {
    private static final String ALGORITHM_VERSION = "price-shared-v1";

    public record ConfirmationResult(
            List<Long> orderIds,
            int allocatedItems,
            int unallocatedItems,
            List<Long> allocatedDemandIds) {
        public ConfirmationResult {
            orderIds = List.copyOf(orderIds);
            allocatedDemandIds = List.copyOf(allocatedDemandIds);
        }
    }

    public ConfirmationResult confirm(
            int year, int week, BatchAllocationPreview displayedPreview)
            throws SQLException {
        validateWeek(year, week);
        Objects.requireNonNull(displayedPreview, "Displayed preview is required.");

        try (Connection connection = DatabaseConnection.getConnection()) {
            // Concurrent changes can cause SQLSTATE 40001. Roll back and let
            // the caller request a fresh preview; never retry an old approval.
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);

            try {
                DemandDao demandDao = new DemandDao(connection);
                WeeklySupplyDao supplyDao = new WeeklySupplyDao(connection);
                demandDao.lockDemandsForWeek(year, week);
                supplyDao.lockSuppliesForWeek(year, week);

                BatchAllocationPreview current = loadPreview(demandDao, supplyDao, year, week);
                if (!stateOf(displayedPreview).equals(stateOf(current))) {
                    throw new IllegalStateException(
                            "Preview has changed. Display a fresh preview and confirm again.");
                }

                RecommendationRunDao runDao = new RecommendationRunDao(connection);
                AllocationDao allocationDao = new AllocationDao(connection);
                OrderDao orderDao = new OrderDao(connection);
                ConfirmationDao confirmationDao = new ConfirmationDao(connection);
                List<Long> orderIds = new ArrayList<>();
                TreeSet<Long> touchedDemands = new TreeSet<>();
                List<Long> allocatedDemands = new ArrayList<>();
                int allocatedItems = 0;
                int unallocatedItems = 0;

                for (BatchItemAllocation result : current.getItems()) {
                    if (result.getAllocations().isEmpty()) {
                        unallocatedItems++;
                        continue;
                    }

                    OpenDemandItem item = result.getItem();
                    if (result.getProposedQuantity().compareTo(item.getQuantity()) != 0) {
                        throw new IllegalStateException("Item must be allocated in full.");
                    }

                    long runId = runDao.insert(
                            item.getDemandItemId(), ALGORITHM_VERSION, "CONFIRMED");

                    for (var entry : result.getAllocations().entrySet()) {
                        AvailableSupply supply = current.findSupply(
                                item.getProductId(), entry.getKey());
                        BigDecimal quantity = entry.getValue();
                        long allocationId = allocationDao.insert(
                                item.getDemandItemId(), supply.getSupplierId(), runId,
                                supply.getSupplyId(), quantity,
                                supply.getUnitPrice(), "CONFIRMED");

                        supplyDao.deductQuantity(supply.getSupplyId(), quantity);
                        long orderId = orderDao.insertForAllocation(
                                item.getShopId(), supply.getSupplierId(), allocationId,
                                item.getProductId(), quantity,
                                supply.getUnitPrice(), "PENDING");
                        orderIds.add(orderId);
                    }

                    allocatedItems++;
                    touchedDemands.add(item.getDemandId());
                }

                for (long demandId : touchedDemands) {
                    if (confirmationDao.markDemandAllocatedIfComplete(demandId)) {
                        allocatedDemands.add(demandId);
                    }
                }

                ConfirmationResult result = new ConfirmationResult(
                        orderIds, allocatedItems, unallocatedItems, allocatedDemands);
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

    private static BatchAllocationPreview loadPreview(
            DemandDao demandDao, WeeklySupplyDao supplyDao, int year, int week)
            throws SQLException {
        List<OpenDemandItem> items = demandDao.findOpenUnallocatedItemsForWeek(year, week);
        TreeSet<Long> productIds = new TreeSet<>();
        for (OpenDemandItem item : items) {
            productIds.add(item.getProductId());
        }

        Map<Long, List<AvailableSupply>> sources = new LinkedHashMap<>();
        for (long productId : productIds) {
            sources.put(productId, supplyDao.findEligible(productId, year, week));
        }
        return SharedSupplyAllocator.preview(items, sources);
    }

    // Compare values rather than object identity or BigDecimal scale.
    // Item order is significant because it determines demand priority.
    private record ItemState(
            long itemId, long demandId, long shopId, String shopName,
            long productId, String productName, String unit, BigDecimal quantity,
            BigDecimal availableBefore, Map<Long, BigDecimal> allocations) {}

    private record SupplyState(
            long supplyId, long supplierId, String supplierName,
            BigDecimal remaining, BigDecimal price) {}

    private record PreviewState(
            List<ItemState> items, Map<Long, Map<Long, SupplyState>> supplies) {}

    private static PreviewState stateOf(BatchAllocationPreview preview) {
        List<ItemState> items = new ArrayList<>();
        for (BatchItemAllocation result : preview.getItems()) {
            OpenDemandItem item = result.getItem();
            Map<Long, BigDecimal> plan = new TreeMap<>();
            result.getAllocations().forEach((id, quantity) ->
                    plan.put(id, quantity.stripTrailingZeros()));
            items.add(new ItemState(
                    item.getDemandItemId(), item.getDemandId(), item.getShopId(),
                    item.getShopName(), item.getProductId(), item.getProductName(),
                    item.getUnit(), item.getQuantity().stripTrailingZeros(),
                    result.getAvailableBefore().stripTrailingZeros(), plan));
        }

        Map<Long, Map<Long, SupplyState>> supplies = new TreeMap<>();
        for (var group : preview.getSuppliesByProduct().entrySet()) {
            Map<Long, SupplyState> rows = new TreeMap<>();
            for (AvailableSupply supply : group.getValue()) {
                rows.put(supply.getSupplyId(), new SupplyState(
                        supply.getSupplyId(), supply.getSupplierId(),
                        supply.getSupplierName(),
                        supply.getRemainingQuantity().stripTrailingZeros(),
                        supply.getUnitPrice().stripTrailingZeros()));
            }
            supplies.put(group.getKey(), rows);
        }
        return new PreviewState(items, supplies);
    }

    private static void validateWeek(int year, int week) {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("Invalid ISO year.");
        }
        int lastWeek = LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 1 || week > lastWeek) {
            throw new IllegalArgumentException("Invalid ISO week for this year.");
        }
    }
}
