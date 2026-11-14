package com.coffeemongers;

import com.coffeeallocation.service.AdminOverviewService;
import com.coffeeallocation.service.WeeklyMarketService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyMarketServiceTest {
    @Test void invalidFiltersFailBeforeConnecting() {
        var service=new AdminOverviewService();
        assertThrows(IllegalArgumentException.class,() -> service.load(0L,2026,46));
        assertThrows(IllegalArgumentException.class,() -> service.load(1L,null,46));
        assertThrows(IllegalArgumentException.class,() -> service.load(1L,2026,null));
        assertThrows(IllegalArgumentException.class,() -> service.load(1L,0,1));
        assertThrows(IllegalArgumentException.class,() -> service.load(1L,10000,1));
        assertThrows(IllegalArgumentException.class,() -> service.load(1L,2021,53));
        assertThrows(IllegalArgumentException.class,() -> service.load(1L,2026,0));
    }
    @Test void isoWeeksCrossCalendarYearsCorrectly() {
        assertEquals(LocalDate.of(2020,12,28),WeeklyMarketService.endingMonday(2020,53));
        assertEquals(LocalDate.of(2021,1,4),WeeklyMarketService.endingMonday(2021,1));
    }
    @Test void eightWeekWindowIncludesEndingWeek() {
        var end=WeeklyMarketService.endingMonday(2021,1);
        assertEquals(LocalDate.of(2020,11,16),WeeklyMarketService.startingMonday(end));
    }
    @Test void lowerYearBoundaryIsClamped() {
        assertEquals(LocalDate.of(1,1,1),WeeklyMarketService.startingMonday(WeeklyMarketService.endingMonday(1,1)));
    }
    @Test void demoHasPairedWeeklyDataAndExplicitProductUnit() {
        var chart=new WeeklyMarketService().demo(LocalDate.of(2021,1,1));
        assertEquals(2020,chart.endYear());assertEquals(53,chart.endWeek());
        assertEquals(8,chart.weeks().size());assertEquals("kg",chart.product().unit());
        assertEquals("2020-W53",chart.weeks().getLast().label());
        assertTrue(chart.weeks().stream().allMatch(w -> w.supply().signum()>0&&w.demand().signum()>0));
    }
}
