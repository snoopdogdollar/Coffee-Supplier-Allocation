package com.coffeeallocation.service;

import com.coffeeallocation.model.SupplierHistory;
import com.coffeeallocation.model.SupplierPerformance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Dashboard policy only. Product/week allocation keeps its existing scoring. */
public final class SupplierRankingService {
    public static final int MINIMUM_REVIEWS = 5;
    public static final int TOP_LIMIT = 3;
    public static final int QUALITY_WEIGHT = 40;
    public static final int QUANTITY_WEIGHT = 30;
    public static final int ON_TIME_WEIGHT = 30;

    public SupplierPerformance.Rankings rank(List<SupplierHistory.Summary> summaries) {
        var suppliers = summaries.stream().sorted(SupplierRankingService::compare)
                .map(this::entry).toList();
        return new SupplierPerformance.Rankings(suppliers, suppliers.stream()
                .filter(SupplierPerformance.Entry::topEligible).limit(TOP_LIMIT).toList());
    }

    private SupplierPerformance.Entry entry(SupplierHistory.Summary summary) {
        long count = summary.reviewCount();
        if (count == 0) return new SupplierPerformance.Entry(summary, null, null, null, null, false);
        return new SupplierPerformance.Entry(summary, rate(summary.qualityOkCount(), count),
                rate(summary.quantityOkCount(), count), rate(summary.onTimeOkCount(), count),
                weightedSuccesses(summary).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP),
                summary.active() && count >= MINIMUM_REVIEWS);
    }

    private static BigDecimal rate(long successes, long count) {
        return BigDecimal.valueOf(successes).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal weightedSuccesses(SupplierHistory.Summary summary) {
        return BigDecimal.valueOf(summary.qualityOkCount()).multiply(BigDecimal.valueOf(QUALITY_WEIGHT))
                .add(BigDecimal.valueOf(summary.quantityOkCount()).multiply(BigDecimal.valueOf(QUANTITY_WEIGHT)))
                .add(BigDecimal.valueOf(summary.onTimeOkCount()).multiply(BigDecimal.valueOf(ON_TIME_WEIGHT)));
    }

    private static int compare(SupplierHistory.Summary left, SupplierHistory.Summary right) {
        if (left.reviewCount() == 0 || right.reviewCount() == 0) {
            int byCount = Long.compare(right.reviewCount(), left.reviewCount());
            return byCount != 0 ? byCount : Long.compare(left.supplierId(), right.supplierId());
        }
        // Cross multiplication compares exact scores before display rounding.
        int byScore = weightedSuccesses(right).multiply(BigDecimal.valueOf(left.reviewCount()))
                .compareTo(weightedSuccesses(left).multiply(BigDecimal.valueOf(right.reviewCount())));
        if (byScore != 0) return byScore;
        int byCount = Long.compare(right.reviewCount(), left.reviewCount());
        return byCount != 0 ? byCount : Long.compare(left.supplierId(), right.supplierId());
    }
}
