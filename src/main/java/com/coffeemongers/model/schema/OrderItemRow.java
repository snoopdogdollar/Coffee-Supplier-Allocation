package com.coffeemongers.model.schema;
import java.math.BigDecimal;
public record OrderItemRow(long orderItemId, long orderId, long productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal totalPrice, String note) { }
