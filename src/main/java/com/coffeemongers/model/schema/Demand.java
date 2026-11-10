package com.coffeemongers.model.schema;
import java.time.LocalDate; import java.time.LocalDateTime;
public record Demand(long demandId, long shopId, LocalDate requestDate, String status, String note, LocalDateTime createdAt) { }
