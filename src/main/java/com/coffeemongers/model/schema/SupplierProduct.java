package com.coffeemongers.model.schema;
import java.time.LocalDateTime;
public record SupplierProduct(long supplierProductId, long supplierId, long productId, boolean isActive, LocalDateTime createdAt) { }
