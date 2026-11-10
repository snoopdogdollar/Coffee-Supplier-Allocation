package com.coffeemongers.model.schema;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record Allocation(long allocationId, long demandItemId, long supplierId, BigDecimal allocatedQuantity, BigDecimal allocatedPrice, String status, LocalDateTime createdAt) { }
