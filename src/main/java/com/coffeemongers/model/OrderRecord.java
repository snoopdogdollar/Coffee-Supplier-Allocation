package com.coffeemongers.model;

import java.time.LocalDate;
import java.util.List;

/** UI summary assembled from ORDERS, ORDER_ITEMS and SUPPLIERS. */
public record OrderRecord(String id, String demandId, String supplierId, String supplierName,
                          LocalDate createdOn, String status, List<OrderItem> items, boolean reviewed) {
    public OrderRecord { items = List.copyOf(items); }
}
