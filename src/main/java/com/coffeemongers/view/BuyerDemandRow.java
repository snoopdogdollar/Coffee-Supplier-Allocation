package com.coffeemongers.view;

import com.coffeeallocation.model.BuyerWorkspaceView.Demand;
import com.coffeemongers.controller.DemandDetailsController;
import com.coffeemongers.model.DemandItem;
import com.coffeemongers.model.DemandRequest;
import com.coffeeallocation.service.WeeklyMarketService;
import java.time.temporal.IsoFields;
import javafx.scene.Node;

/** Presentation only; units stay on each product line. */
public record BuyerDemandRow(DemandRequest request,Demand saved) {
    public static BuyerDemandRow of(Demand demand) {
        var items=demand.items().stream().map(i -> new DemandItem(i.productName(),i.requested(),i.unit(),i.note()==null?"":i.note())).toList();
        return new BuyerDemandRow(new DemandRequest(Long.toString(demand.id()),demand.products(),demand.requestDate(),"",
                demand.note()==null?"":demand.note(),items,WeeklyMarketService.endingMonday(demand.year(),demand.week())),demand);
    }
    public static BuyerDemandRow demo(DemandRequest request) {return new BuyerDemandRow(request,null);}
    public String id(){return request.id();}
    public String products(){return request.items().stream().map(DemandItem::productName).distinct().reduce((a,b)->a+", "+b).orElse("—");}
    public String quantities(){return request.items().stream().map(i -> i.quantity().stripTrailingZeros().toPlainString()+" "+i.unit()).reduce((a,b)->a+", "+b).orElse("—");}
    public String week(){return saved!=null?saved.deliveryWeek():"%04d-W%02d".formatted(request.requiredDeliveryDate().get(IsoFields.WEEK_BASED_YEAR),request.requiredDeliveryDate().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));}
    public String status(){return saved==null?"SUBMITTED · DEMO":saved.status();}
    public String progress(){return saved==null?"Awaiting Admin allocation":saved.progress();}
    public void open(Node source) {
        SceneNavigator.show(source,"/com/coffeemongers/createdemand/DemandDetails.fxml","Coffee Mongers · Demand Details",controller -> {
            var details=(DemandDetailsController)controller;
            if(saved==null)details.setDemand(request);else details.showSavedDemand(saved.id());
        });
    }
}
