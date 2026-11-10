package com.coffeemongers.model.viewmodel;

/** Presentation-only featured supplier campaign; no advertising table exists in the supplied schema. */
public record SupplierAdvertisement(String supplierName,String headline,String message,String imageResource,String actionText) { }
