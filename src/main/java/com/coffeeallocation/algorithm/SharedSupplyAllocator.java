// Use the SharedSupplyAllocator to allocate shared supply among multiple demands. This class is responsible for implementing the allocation algorithm that distributes available supply to various demands based on their requirements and priorities.
package com.coffeeallocation.algorithm;

import com.coffeeallocation.model.AvailableSupply;
import com.coffeeallocation.model.BatchAllocationPreview;
import com.coffeeallocation.model.BatchItemAllocation;
import com.coffeeallocation.model.OpenDemandItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SharedSupplyAllocator {
    private SharedSupplyAllocator() {
    }

    /**
     * The caller orders items by request_date, demand_id, demand_item_id.
     * Items must have no previous allocations. All supply groups concern one ISO week.
     * The remaining quantities below are local to this preview, not database reservations.
     */
    public static BatchAllocationPreview preview(
            List<OpenDemandItem> orderedItems,
            Map<Long, List<AvailableSupply>> suppliesByProduct) {

        List<OpenDemandItem> items = List.copyOf(orderedItems);
        Set<Long> itemIds = new HashSet<>();
        for (OpenDemandItem item : items) {
            if (item.getDemandItemId() <= 0 || item.getProductId() <= 0
                    || !itemIds.add(item.getDemandItemId())) {
                throw new IllegalArgumentException("Invalid or duplicate demand item ID.");
            }
        }

        Map<Long, List<AvailableSupply>> groups = new LinkedHashMap<>();
        Map<Long, BigDecimal> remaining = new LinkedHashMap<>();
        for (var entry : suppliesByProduct.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0) {
                throw new IllegalArgumentException("Product ID must be positive.");
            }
            List<AvailableSupply> sources = List.copyOf(entry.getValue());
            groups.put(entry.getKey(), sources);
            for (AvailableSupply source : sources) {
                if (source.getSupplyId() <= 0 || source.getRemainingQuantity() == null
                        || source.getRemainingQuantity().signum() < 0) {
                    throw new IllegalArgumentException("Invalid supply balance.");
                }
                if (remaining.putIfAbsent(source.getSupplyId(),
                        source.getRemainingQuantity()) != null) {
                    throw new IllegalArgumentException("Duplicate supply ID across supply groups.");
                }
            }
        }

        List<BatchItemAllocation> results = new ArrayList<>();
        for (OpenDemandItem item : items) {
            List<AvailableSupply> adjusted = new ArrayList<>();
            BigDecimal availableBefore = BigDecimal.ZERO;

            for (AvailableSupply source : groups.getOrDefault(item.getProductId(), List.of())) {
                BigDecimal balance = remaining.get(source.getSupplyId());
                availableBefore = availableBefore.add(balance);
                adjusted.add(new AvailableSupply(
                        source.getSupplyId(), source.getSupplierId(), source.getSupplierName(),
                        balance, source.getUnitPrice()));
            }

            Map<Long, BigDecimal> plan = AllocationAlgorithm.preview(item.getQuantity(), adjusted);
            // An unsuccessful all-or-nothing item consumes nothing.
            for (var allocation : plan.entrySet()) {
                BigDecimal balance = remaining.get(allocation.getKey())
                        .subtract(allocation.getValue());
                if (balance.signum() < 0) {
                    throw new IllegalStateException("A preview cannot exceed the supply balance.");
                }
                remaining.put(allocation.getKey(), balance);
            }
            results.add(new BatchItemAllocation(item, availableBefore, plan));
        }

        return new BatchAllocationPreview(results, groups, remaining);
    }
}

