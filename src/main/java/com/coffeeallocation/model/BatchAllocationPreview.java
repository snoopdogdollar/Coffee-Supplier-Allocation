// Result of allocation for a single item in a batch. This is an immutable object that contains the item, the available quantity before allocation, and the proposed allocations to each demand.
package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BatchAllocationPreview {
    private final List<BatchItemAllocation> items;
    private final Map<Long, List<AvailableSupply>> suppliesByProduct;
    private final Map<Long, BigDecimal> remainingBySupply;

    public BatchAllocationPreview(List<BatchItemAllocation> items,
            Map<Long, List<AvailableSupply>> suppliesByProduct,
            Map<Long, BigDecimal> remainingBySupply) {
        this.items = List.copyOf(items);
        Map<Long, List<AvailableSupply>> copied = new LinkedHashMap<>();
        for (var entry : suppliesByProduct.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.suppliesByProduct = Collections.unmodifiableMap(copied);
        this.remainingBySupply = Collections.unmodifiableMap(
                new LinkedHashMap<>(remainingBySupply));
    }

    public List<BatchItemAllocation> getItems() { return items; }
    public Map<Long, List<AvailableSupply>> getSuppliesByProduct() { return suppliesByProduct; }
    public Map<Long, BigDecimal> getRemainingBySupply() { return remainingBySupply; }

    public AvailableSupply findSupply(long productId, long supplyId) {
        for (AvailableSupply supply : suppliesByProduct.getOrDefault(productId, List.of())) {
            if (supply.getSupplyId() == supplyId) {
                return supply;
            }
        }
        throw new IllegalArgumentException("Unknown supply ID: " + supplyId);
    }
}
