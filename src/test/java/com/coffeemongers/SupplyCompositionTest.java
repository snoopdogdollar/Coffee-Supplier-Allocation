package com.coffeemongers;

import com.coffeeallocation.model.SupplyComposition.*;
import com.coffeemongers.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyCompositionTest {
    private Snapshot snapshot(){return new Snapshot(LocalDate.of(2026,11,9),LocalDateTime.of(2026,11,15,12,0),List.of(new Product(1,"Arabica","kg"),new Product(2,"Robusta","kg"),new Product(3,"Capsules","bag")),List.of(new Contribution(1,1,"A",new BigDecimal("10.25"),new BigDecimal("6.75")),new Contribution(1,2,"B",new BigDecimal("20"),new BigDecimal("20"))));}
    @Test void defaultsAndTotalsUseExactRemainingQuantities(){var s=snapshot();assertEquals(new BigDecimal("26.75"),s.total(1,Measure.REMAINING));assertEquals(new BigDecimal("30.25"),s.total(1,Measure.DECLARED));assertEquals(new BigDecimal("6.75"),s.amount(1,1,Measure.REMAINING));}
    @Test void percentagesHandleZeroSupply(){var s=snapshot();assertEquals(new BigDecimal("25.2"),s.percentage(1,1,Measure.REMAINING));assertEquals(BigDecimal.ZERO,s.percentage(2,1,Measure.REMAINING));}
    @Test void zeroProductsAndUnitsRemainRepresented(){var s=snapshot();assertEquals(3,s.products().size());assertEquals(List.of("kg","bag"),s.units());assertEquals(BigDecimal.ZERO,s.total(3,Measure.REMAINING));}
    @Test void invalidWeekFailsBeforeOpeningDatabase(){assertThrows(IllegalArgumentException.class,() -> new SupplyCompositionService().load(LocalDate.of(0,1,4)));}
    @Test void demoCategoriesFollowActiveCatalogChanges(){var market=new InMemoryMarketplaceService();var service=new SupplyCompositionService();int before=service.demo(market,null).products().size();var product=market.addCoffeeProduct("New coffee","","kg","Arabica");assertEquals(before+1,service.demo(market,null).products().size());market.setCoffeeProductActive(product.productId(),false);assertEquals(before,service.demo(market,null).products().size());}
}
