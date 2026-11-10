package com.coffeemongers.model.schema;
import java.math.BigDecimal;
public record DemandItemRow(long demandItemId, long demandId, long productId, BigDecimal quantity, String note) { }
