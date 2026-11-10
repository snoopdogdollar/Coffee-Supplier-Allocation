package com.coffeemongers.model.schema;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record SupplierWeeklySupply(long supplyId, long supplierProductId, int week, int year, BigDecimal initialQuantity, BigDecimal remainingQuantity, BigDecimal unitPrice, LocalDateTime createdAt) { }
