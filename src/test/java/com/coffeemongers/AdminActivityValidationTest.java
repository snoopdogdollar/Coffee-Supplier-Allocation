package com.coffeemongers;

import com.coffeeallocation.service.AdminActivityService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminActivityValidationTest {
    private final AdminActivityService service=new AdminActivityService();
    @Test void invalidSearchIsRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.load("x".repeat(101)));
        assertThrows(IllegalArgumentException.class,() -> service.load("bad\0search"));
    }
    @Test void invalidCursorsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.orders("",0L));
        assertThrows(IllegalArgumentException.class,() -> service.demands("",-1L));
    }
    @Test void invalidDetailIdsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.orderDetails(0));
        assertThrows(IllegalArgumentException.class,() -> service.demandDetails(-1));
    }
}
