package com.coffeeallocation.model;

import java.time.LocalDateTime;

public record BusinessPartyDetails(long id, String name, String contactPerson, String phone,
                                   String email, String address, boolean active,
                                   LocalDateTime createdAt) { }
