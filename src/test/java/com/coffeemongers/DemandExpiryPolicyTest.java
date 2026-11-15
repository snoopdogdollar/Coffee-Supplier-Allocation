package com.coffeemongers;

import com.coffeeallocation.model.DemandExpiryPolicy;
import com.coffeemongers.view.DemandStatusPresentation;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DemandExpiryPolicyTest {
    @Test void expiresAtVietnamMondayEvenWhileItIsSundayInUtc() {
        var lastSecond=Instant.parse("2026-11-15T16:59:59Z").atZone(DemandExpiryPolicy.BUSINESS_ZONE).toLocalDate();
        var monday=Instant.parse("2026-11-15T17:00:00Z").atZone(DemandExpiryPolicy.BUSINESS_ZONE).toLocalDate();
        assertFalse(DemandExpiryPolicy.isPastWeek(2026,46,lastSecond));
        assertTrue(DemandExpiryPolicy.isPastWeek(2026,46,monday));
        assertFalse(DemandExpiryPolicy.isPastWeek(2026,47,monday));
        assertFalse(DemandExpiryPolicy.isPastWeek(2026,48,monday));
    }
    @Test void usesIsoYearAcrossNewYearAndWeek53() {
        assertFalse(DemandExpiryPolicy.isPastWeek(2020,53,LocalDate.of(2021,1,3)));
        assertTrue(DemandExpiryPolicy.isPastWeek(2020,53,LocalDate.of(2021,1,4)));
        assertFalse(DemandExpiryPolicy.isPastWeek(2021,1,LocalDate.of(2021,1,4)));
    }
    @Test void rejectsInvalidWeeksAndOldRequests() {
        assertThrows(IllegalArgumentException.class,() -> DemandExpiryPolicy.isPastWeek(2021,53,LocalDate.of(2021,1,4)));
        assertThrows(IllegalArgumentException.class,() -> DemandExpiryPolicy.requireActiveWeek(2020,53,LocalDate.of(2021,1,4)));
        assertDoesNotThrow(() -> DemandExpiryPolicy.requireActiveWeek(2021,1,LocalDate.of(2021,1,4)));
    }
    @Test void expiryAndCancellationExplainDifferentCauses() {
        assertTrue(DemandStatusPresentation.explanation("EXPIRED").contains("delivery week has ended"));
        assertTrue(DemandStatusPresentation.explanation("EXPIRED").contains("orders remain valid"));
        assertTrue(DemandStatusPresentation.explanation("CANCELLED").contains("cancelled explicitly"));
    }
}
