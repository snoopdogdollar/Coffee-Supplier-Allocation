package com.coffeemongers;

import com.coffeeallocation.model.SupplierHistory;
import com.coffeeallocation.service.SupplierRankingService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SupplierRankingTest {
    private final SupplierRankingService service = new SupplierRankingService();
    private SupplierHistory.Summary summary(long id, long count, long quality, long quantity, long onTime, boolean active) {
        return new SupplierHistory.Summary(id,"Supplier "+id,count,quality,quantity,onTime,
                count == 0 ? null : LocalDateTime.of(2026,11,1,12,0),active);
    }
    @Test void binaryFeedbackAggregatesIntoWeightedRates() {
        var row=service.rank(List.of(summary(1,10,8,9,7,true))).suppliers().getFirst();
        assertEquals(new BigDecimal("80.00"),row.qualityRate());
        assertEquals(new BigDecimal("90.00"),row.quantityRate());
        assertEquals(new BigDecimal("70.00"),row.onTimeRate());
        assertEquals(new BigDecimal("80.00"),row.score());assertTrue(row.topEligible());
    }
    @Test void noReviewsHasNoScoreInsteadOfZero() {
        var report=service.rank(List.of(summary(1,0,0,0,0,true)));
        var row=report.suppliers().getFirst();
        assertNull(row.score());assertNull(row.qualityRate());assertNull(row.quantityRate());assertNull(row.onTimeRate());
        assertFalse(row.topEligible());assertTrue(report.topSuppliers().isEmpty());
    }
    @Test void minimumReviewBoundaryAndInactiveHistory() {
        var report=service.rank(List.of(summary(1,4,4,4,4,true),summary(2,5,5,5,5,true),
                summary(3,10,10,10,10,false),summary(4,1,0,0,0,true)));
        assertEquals(List.of(2L),report.topSuppliers().stream().map(e -> e.history().supplierId()).toList());
        assertEquals(4,report.suppliers().size());
        assertEquals(new BigDecimal("0.00"),report.suppliers().getLast().score());
    }
    @Test void tiesPreferMoreReviewsThenStableSupplierIdAndLimitTopThree() {
        var report=service.rank(List.of(summary(5,5,5,5,5,true),summary(4,10,10,10,10,true),
                summary(2,10,10,10,10,true),summary(3,10,10,10,10,true)));
        assertEquals(List.of(2L,3L,4L),report.topSuppliers().stream().map(e -> e.history().supplierId()).toList());
        assertEquals(4,report.suppliers().size());
    }
    @Test void scoreIsComputedBeforeRoundingRates() {
        var row=service.rank(List.of(summary(1,3,2,1,1,true))).suppliers().getFirst();
        assertEquals(new BigDecimal("46.67"),row.score());
    }
    @Test void rankingComparesExactScoreEvenWhenDisplayedScoresTie() {
        var report=service.rank(List.of(summary(1,100000,50000,50000,50000,true),
                summary(2,99999,50000,50000,50000,true)));
        assertEquals(List.of(2L,1L),report.topSuppliers().stream().map(e -> e.history().supplierId()).toList());
        assertEquals(report.suppliers().getFirst().score(),report.suppliers().getLast().score());
    }
    @Test void emptyDataIsAnEmptyRanking() {
        assertTrue(service.rank(List.of()).suppliers().isEmpty());
        assertTrue(service.rank(List.of()).topSuppliers().isEmpty());
    }
}
