package com.coffeemongers.view;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/** Consistent, explicit feedback without changing the operation's message. */
public final class ViewFeedback {
    public enum Kind { INFO, LOADING, SUCCESS, ERROR }
    private ViewFeedback() {}
    public static void show(Label label,Kind kind,String message) {
        label.getStyleClass().removeAll("feedback-info","feedback-loading","feedback-success","feedback-error");
        if(!label.getStyleClass().contains("feedback"))label.getStyleClass().add("feedback");
        label.getStyleClass().add("feedback-"+kind.name().toLowerCase(java.util.Locale.ROOT));
        label.setText(message);label.setWrapText(true);
        label.setVisible(message!=null&&!message.isBlank());label.setManaged(label.isVisible());
        label.setGraphic(null);
        if(kind==Kind.LOADING&&label.isVisible()) {
            var spinner=new ProgressIndicator();spinner.setMinSize(16,16);spinner.setPrefSize(16,16);spinner.setMaxSize(16,16);
            label.setGraphic(spinner);label.setGraphicTextGap(8);
        }
    }
}
