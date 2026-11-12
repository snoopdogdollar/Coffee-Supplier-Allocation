package com.coffeemongers;

import com.coffeeallocation.service.AdminCoffeeProductService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminCoffeeProductValidationTest {
    private final AdminCoffeeProductService service = new AdminCoffeeProductService();

    @Test void requiredFieldsRejectBlankValuesBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> service.add(null, null, "kg", null));
        assertThrows(IllegalArgumentException.class, () -> service.add(" \t\n", "", "kg", ""));
        assertThrows(IllegalArgumentException.class, () -> service.add("Coffee", "", " ", ""));
    }

    @Test void schemaLimitsAndNulCharactersAreValidatedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> service.add("a".repeat(101), "", "kg", ""));
        assertThrows(IllegalArgumentException.class, () -> service.add("Coffee", "", "a".repeat(21), ""));
        assertThrows(IllegalArgumentException.class, () -> service.add("Coffee", "a".repeat(256), "kg", ""));
        assertThrows(IllegalArgumentException.class, () -> service.add("Coffee", "", "kg", "a".repeat(51)));
        assertThrows(IllegalArgumentException.class, () -> service.add("Coffee", "hidden\0text", "kg", ""));
    }

    @Test void invalidIdsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(0, true));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(-1, false));
    }
}
