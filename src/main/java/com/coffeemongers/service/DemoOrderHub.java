package com.coffeemongers.service;

import com.coffeemongers.model.OrderRecord;
import java.util.*;

/** Shared demo marketplace orders; account-private demand lists remain inside each marketplace service. */
public final class DemoOrderHub {
    private final Map<String,OrderRecord> orders=new LinkedHashMap<>();
    public synchronized void publish(OrderRecord order){orders.put(order.id(),order);}
    public synchronized Optional<OrderRecord> find(String id){return Optional.ofNullable(orders.get(id));}
    public synchronized List<OrderRecord> all(){return List.copyOf(orders.values());}
    public synchronized List<OrderRecord> forSupplier(String supplierId){return orders.values().stream().filter(o->o.supplierId().equals(supplierId)).toList();}
    public synchronized OrderRecord updateStatus(String id,String status){OrderRecord old=orders.get(id);if(old==null)throw new IllegalArgumentException("Order not found.");OrderRecord updated=new OrderRecord(old.id(),old.demandId(),old.supplierId(),old.supplierName(),old.createdOn(),status,old.items(),old.reviewed());orders.put(id,updated);return updated;}
}
