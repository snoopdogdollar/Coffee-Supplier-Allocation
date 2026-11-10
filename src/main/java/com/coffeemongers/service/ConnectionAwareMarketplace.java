package com.coffeemongers.service;

/** Marker for a configured remote service that can report current backend availability. */
public interface ConnectionAwareMarketplace {
    boolean isConnected();
}
