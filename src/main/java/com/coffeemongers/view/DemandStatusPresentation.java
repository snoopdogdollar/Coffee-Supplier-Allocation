package com.coffeemongers.view;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;

/** Demand lifecycle labels; order statuses have their own lifecycle. */
public final class DemandStatusPresentation {
    private DemandStatusPresentation() {}
    public static String explanation(String status) {
        return switch(status) {
            case "EXPIRED" -> "The delivery week has ended. Unfulfilled quantities are closed and will not carry over. Confirmed allocations and orders remain valid.";
            case "CANCELLED" -> "This demand was cancelled explicitly. Cancellation is different from expiry at the end of the delivery week.";
            default -> "Requested, confirmed allocation and remaining quantities are shown separately for each product.";
        };
    }
    public static void apply(Label label,String status) {
        label.setText(status);
        label.getStyleClass().removeAll("demand-expired","demand-cancelled");
        if("EXPIRED".equals(status))label.getStyleClass().add("demand-expired");
        if("CANCELLED".equals(status))label.getStyleClass().add("demand-cancelled");
    }
    public static TableCell<BuyerDemandRow,String> cell() {
        return new TableCell<>() {
            private final Label badge=new Label();
            {badge.getStyleClass().add("status-pill");}
            @Override protected void updateItem(String status,boolean empty) {
                super.updateItem(status,empty);setText(null);
                if(empty||status==null){setGraphic(null);return;}
                apply(badge,status);setGraphic(badge);
            }
        };
    }
}
