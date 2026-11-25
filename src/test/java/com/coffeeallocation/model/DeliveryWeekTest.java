package com.coffeeallocation.model;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryWeekTest {
    @Test void usesTheIsoYearAtCalendarYearBoundaries() {
        assertEquals(new DeliveryWeek(2026, 53), DeliveryWeek.from(LocalDate.of(2027, 1, 1)));
        assertEquals(new DeliveryWeek(2027, 1), DeliveryWeek.from(LocalDate.of(2027, 1, 4)));
        assertEquals(new DeliveryWeek(2020, 1), DeliveryWeek.from(LocalDate.of(2019, 12, 30)));
    }

    @Test void validatesWeek53AndYearBounds() {
        assertDoesNotThrow(() -> new DeliveryWeek(2026, 53));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryWeek(2025, 53));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryWeek(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryWeek(10000, 1));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryWeek(2026, 0));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryWeek(2026, 54));
    }

    @Test void businessTimezoneChangesWeeksBeforeUtc() {
        var instant = Instant.parse("2026-11-22T17:00:00Z");
        assertEquals(new DeliveryWeek(2026, 48), DeliveryWeek.from(
                instant.atZone(DemandExpiryPolicy.BUSINESS_ZONE).toLocalDate()));
    }
}
