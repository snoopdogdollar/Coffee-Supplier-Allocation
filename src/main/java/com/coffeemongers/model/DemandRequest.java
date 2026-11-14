package com.coffeemongers.model;

import java.time.LocalDate;
import java.util.List;

/** UI submission/read model. Persisted values map to DEMANDS and DEMAND_ITEMS. */
public record DemandRequest(String id, String title, LocalDate requestDate,
                            String shopAddress, String notes, List<DemandItem> items, LocalDate requiredDeliveryDate) {
    public DemandRequest { items = List.copyOf(items); }
    public DemandRequest(String id,String title,LocalDate requestDate,String shopAddress,String notes,List<DemandItem> items) {
        this(id,title,requestDate,shopAddress,notes,items,requestDate);
    }
}
