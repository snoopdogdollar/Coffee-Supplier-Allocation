package com.coffeemongers.service;

import com.coffeeallocation.model.SupplierSupplyView;
import com.coffeeallocation.service.SupplierWeeklySupplyService;
import com.coffeemongers.model.WeeklySupplyRecord;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public final class SupplierSupplyService {
    private final long supplierId;
    private final SupplierWeeklySupplyService backend;

    public record Snapshot(SupplierSupplyView.Catalog catalog, List<WeeklySupplyRecord> rows) {
        public Snapshot {
            rows = List.copyOf(rows);
        }
    }

    public SupplierSupplyService(long supplierId) {
        this.backend = new SupplierWeeklySupplyService(supplierId);
        this.supplierId = supplierId;
    }

    public Snapshot load(LocalDate week) throws SQLException {
        var result = backend.load(week);
        List<WeeklySupplyRecord> rows = result.rows().stream().map(row -> {
            LocalDate monday = LocalDate.of(row.year(), 1, 4)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(row.week() - 1);
            return new WeeklySupplyRecord(Long.toString(row.supplyId()), Long.toString(supplierId),
                    row.productName(), monday, row.initialQuantity(), row.remainingQuantity(),
                    row.unitPrice(), row.unit(), row.supplierProductId());
        }).toList();
        return new Snapshot(result.catalog(), rows);
    }

    public long save(Long supplyId, long supplierProductId, LocalDate week,
                     BigDecimal initialQuantity, BigDecimal unitPrice) throws SQLException {
        return backend.save(supplyId, supplierProductId, week, initialQuantity, unitPrice);
    }

    public void delete(long supplyId) throws SQLException {
        backend.delete(supplyId);
    }
}
