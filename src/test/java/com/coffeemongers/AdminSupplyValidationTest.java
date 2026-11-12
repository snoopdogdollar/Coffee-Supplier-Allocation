package com.coffeemongers;

import com.coffeeallocation.service.AdminSupplyService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminSupplyValidationTest {
    private final AdminSupplyService service=new AdminSupplyService();
    @Test void invalidIsoYearAndWeekAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page(0,1,"","",null));
        assertThrows(IllegalArgumentException.class,() -> service.page(10000,1,"","",null));
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,0,"","",null));
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,54,"","",null));
        assertThrows(IllegalArgumentException.class,() -> service.page(2025,53,"","",null));
    }
    @Test void invalidCursorsAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,46,"","",0L));
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,46,"","",-1L));
        assertThrows(IllegalArgumentException.class,() -> service.allWeeks("","",0L));
    }
    @Test void filterLimitsAndUnsupportedCharactersAreRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,46,"x".repeat(101),"",null));
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,46,"","x".repeat(101),null));
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,46,"bad\0filter","",null));
        assertThrows(IllegalArgumentException.class,() -> service.page(2026,46,"","bad\0filter",null));
        assertThrows(IllegalArgumentException.class,() -> service.allWeeks("x".repeat(101),"",null));
        assertThrows(IllegalArgumentException.class,() -> service.allWeeks("","bad\0filter",null));
    }
}
