// Result of allocation for a single item in a batch. This is an immutable object that contains the item, the available quantity before allocation, and the proposed allocations to each demand.
package com.coffeeallocation.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BatchItemAllocation {
    private final OpenDemandItem item;
    private final BigDecimal availableBefore;
    private final Map<Long, BigDecimal> allocations;

    public BatchItemAllocation(OpenDemandItem item, BigDecimal availableBefore,
            Map<Long, BigDecimal> allocations) {
        this.item = item;
        this.availableBefore = availableBefore;
        this.allocations = Collections.unmodifiableMap(new LinkedHashMap<>(allocations));
    }

    public OpenDemandItem getItem() { return item; }
    public BigDecimal getAvailableBefore() { return availableBefore; }
    public Map<Long, BigDecimal> getAllocations() { return allocations; }

    public BigDecimal getProposedQuantity() {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal quantity : allocations.values()) {
            total = total.add(quantity);
        }
        return total;
    }

    public BigDecimal getUnallocatedQuantity() {
        return item.getQuantity().subtract(getProposedQuantity());
    }

    public BigDecimal getSupplyShortfall() {
        return item.getQuantity().subtract(availableBefore).max(BigDecimal.ZERO);
    }
}
