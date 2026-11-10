package com.coffeemongers.model.schema;
import java.time.LocalDateTime;
public record RecommendationRun(long runId, long demandItemId, LocalDateTime runTime, String algorithmVersion, String status, String note) { }
