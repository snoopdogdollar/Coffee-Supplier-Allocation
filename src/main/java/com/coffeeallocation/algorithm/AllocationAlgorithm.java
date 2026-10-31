package com.coffeeallocation.algorithm;

import com.coffeeallocation.model.AvailableSupply;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AllocationAlgorithm {

    private AllocationAlgorithm() {
    }

    public static Map<Long, BigDecimal> preview(
            BigDecimal remainingDemand,
            List<AvailableSupply> eligibleSupplies) {

        validateQuantity(remainingDemand, false);

        List<AvailableSupply> supplies = List.copyOf(eligibleSupplies);
        Set<Long> supplyIds = new HashSet<>();
        BigDecimal totalAvailable = BigDecimal.ZERO;

        for (AvailableSupply supply : supplies) {
            if (supply.getSupplyId() <= 0 || supply.getSupplierId() <= 0) {
                throw new IllegalArgumentException("IDs must be positive.");
            }

            if (!supplyIds.add(supply.getSupplyId())) {
                throw new IllegalArgumentException("Duplicate supply ID.");
            }

            validateQuantity(supply.getRemainingQuantity(), true);

            if (supply.getUnitPrice() == null
                    || supply.getUnitPrice().signum() < 0) {
                throw new IllegalArgumentException("Invalid unit price.");
            }

            totalAvailable = totalAvailable.add(
                    supply.getRemainingQuantity()
            );
        }

        // Không đủ toàn bộ nhu cầu: không tạo phân bổ.
        if (totalAvailable.compareTo(remainingDemand) < 0) {
            return Map.of();
        }

        Map<Long, BigDecimal> allocations = new LinkedHashMap<>();
        BigDecimal stillNeeded = remainingDemand;

        for (AvailableSupply supply : PricePriority.rank(supplies)) {
            BigDecimal quantity = stillNeeded.min(
                    supply.getRemainingQuantity()
            );

            if (quantity.signum() > 0) {
                allocations.put(supply.getSupplyId(), quantity);
                stillNeeded = stillNeeded.subtract(quantity);
            }

            if (stillNeeded.signum() == 0) {
                break;
            }
        }

        return Collections.unmodifiableMap(allocations);
    }

    private static void validateQuantity(
            BigDecimal quantity, boolean allowZero) {

        if (quantity == null
                || quantity.signum() < 0 // negative quantity is invalid
                || (!allowZero && quantity.signum() == 0)
                || quantity.stripTrailingZeros().scale() > 2) { // more than 2 decimal places is invalid
            throw new IllegalArgumentException("Invalid quantity.");
        }
    }
}