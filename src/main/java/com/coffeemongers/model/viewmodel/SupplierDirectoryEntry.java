package com.coffeemongers.model.viewmodel;

import java.math.BigDecimal;
import java.util.List;

/** Buyer-facing supplier directory presentation data assembled through MarketplaceService. */
public record SupplierDirectoryEntry(String supplierId,String name,String location,BigDecimal matchScore,List<String> availableProducts) {
    public SupplierDirectoryEntry { availableProducts=List.copyOf(availableProducts); }
}
