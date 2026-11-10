package com.coffeemongers.model.schema;
import java.math.BigDecimal;
public record SupplierScore(long scoreId, long runId, long supplierId, BigDecimal priceScore, BigDecimal reliabilityScore, BigDecimal otherScore, BigDecimal finalScore, int rank) { }
