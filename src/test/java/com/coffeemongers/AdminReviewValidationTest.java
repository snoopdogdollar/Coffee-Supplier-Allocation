package com.coffeemongers;

import com.coffeeallocation.service.AdminReviewService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminReviewValidationTest {
    private final AdminReviewService service=new AdminReviewService();
    @Test void invalidFiltersAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page("x".repeat(101),"",null,null));
        assertThrows(IllegalArgumentException.class,() -> service.page("","bad\0filter",null,null));
    }
    @Test void invalidOrderIdsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page("","",0L,null));
        assertThrows(IllegalArgumentException.class,() -> service.page("","",-1L,null));
    }
    @Test void invalidCursorsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page("","",null,0L));
        assertThrows(IllegalArgumentException.class,() -> service.page("","",null,-1L));
    }
}
