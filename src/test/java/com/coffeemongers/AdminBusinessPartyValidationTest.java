package com.coffeemongers;

import com.coffeeallocation.model.BusinessPartyType;
import com.coffeeallocation.service.AdminBusinessPartyService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminBusinessPartyValidationTest {
    @Test void bothCatalogsRequireNamesBeforeConnecting() {
        for (var type : BusinessPartyType.values()) {
            var service = new AdminBusinessPartyService(type);
            assertThrows(IllegalArgumentException.class, () -> service.add(null, null, null, null, null));
            assertThrows(IllegalArgumentException.class, () -> service.add(" \t\n", "", "", "", ""));
        }
    }

    @Test void bothCatalogsValidateSchemaLimitsAndUnsupportedCharacters() {
        for (var type : BusinessPartyType.values()) {
            var service = new AdminBusinessPartyService(type);
            assertThrows(IllegalArgumentException.class, () -> service.add("a".repeat(101), "", "", "", ""));
            assertThrows(IllegalArgumentException.class, () -> service.add("Business", "a".repeat(101), "", "", ""));
            assertThrows(IllegalArgumentException.class, () -> service.add("Business", "", "1".repeat(21), "", ""));
            assertThrows(IllegalArgumentException.class, () -> service.add("Business", "", "", "a".repeat(101), ""));
            assertThrows(IllegalArgumentException.class, () -> service.add("Business", "", "", "", "a".repeat(256)));
            assertThrows(IllegalArgumentException.class, () -> service.add("Business", "Contact\0Name", "", "", ""));
        }
    }

    @Test void invalidCatalogAndIdsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> new AdminBusinessPartyService(null));
        for (var type : BusinessPartyType.values()) {
            var service = new AdminBusinessPartyService(type);
            assertThrows(IllegalArgumentException.class, () -> service.changeStatus(0, true));
            assertThrows(IllegalArgumentException.class, () -> service.changeStatus(-1, false));
        }
    }
}
