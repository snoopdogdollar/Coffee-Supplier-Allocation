package com.coffeemongers;

import com.coffeeallocation.service.AdminSupplierProductService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminSupplierProductValidationTest {
    private final AdminSupplierProductService service=new AdminSupplierProductService();
    @Test void filtersAreValidatedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.load("x".repeat(101),""));
        assertThrows(IllegalArgumentException.class,() -> service.load("","bad\0filter"));
        assertThrows(IllegalArgumentException.class,() -> service.page("","x".repeat(101),null));
    }
    @Test void invalidCursorIsRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page("","",0L));
        assertThrows(IllegalArgumentException.class,() -> service.page("","",-1L));
    }
    @Test void invalidOwnershipIdsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.offer(0,1));
        assertThrows(IllegalArgumentException.class,() -> service.offer(1,0));
        assertThrows(IllegalArgumentException.class,() -> service.stop(-1,1));
        assertThrows(IllegalArgumentException.class,() -> service.stop(1,-1));
    }
}
