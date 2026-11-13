package com.coffeeallocation.model;

import java.time.LocalDateTime;
import java.util.List;

public final class SupplierHistory {
    private SupplierHistory() {}

    public record Cutoff(LocalDateTime timestamp, String timeZone) {}

    public record Summary(
            long supplierId,
            String supplierName,
            long reviewCount,
            long qualityOkCount,
            long quantityOkCount,
            long onTimeOkCount,
            LocalDateTime latestReviewAt,
            boolean active) {}

    public record Report(Cutoff cutoff, List<Summary> suppliers) {
        public Report {
            suppliers = List.copyOf(suppliers);
        }
    }
}
