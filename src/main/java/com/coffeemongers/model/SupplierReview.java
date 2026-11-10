package com.coffeemongers.model;

import java.time.LocalDate;

/** UI review draft mapped to SUPPLIER_REVIEWS boolean columns. */
public record SupplierReview(String supplierId, String orderId, boolean qualityOk,
                             boolean quantityOk, boolean onTimeOk, String comment, LocalDate createdOn) { }
