package com.coffeeallocation.algorithm;

import com.coffeeallocation.model.AvailableSupply;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PricePriority {

    private PricePriority() {
    }

    public static List<AvailableSupply> rank(
            List<AvailableSupply> supplies) {

        List<AvailableSupply> ranked = new ArrayList<>(supplies);

        ranked.sort(
            Comparator.comparing(AvailableSupply::getUnitPrice)
                .thenComparingLong(AvailableSupply::getSupplierId)
        );

        return List.copyOf(ranked);
    }
}