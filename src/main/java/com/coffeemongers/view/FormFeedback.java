package com.coffeemongers.view;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Field errors stay with their controls and clear when those controls are edited. */
public final class FormFeedback {
    private static final PseudoClass INVALID=PseudoClass.getPseudoClass("invalid");
    private final Map<Control,Label> fields=new LinkedHashMap<>();
    private Control firstInvalid;
    public void register(Control control,Label error,ObservableValue<?>... values) {
        fields.put(control,error);error.getStyleClass().add("field-error");error.setWrapText(true);error.setLabelFor(control);
        clear(control);for(var value:values)value.addListener((o,old,current) -> clear(control));
    }
    public void clear(Control control) {
        control.pseudoClassStateChanged(INVALID,false);control.setAccessibleHelp(null);
        Label error=fields.get(control);if(error!=null){error.setText("");error.setVisible(false);error.setManaged(false);}
    }
    public void clearAll(){firstInvalid=null;fields.keySet().forEach(this::clear);}
    public void remove(Control... controls){for(var control:controls)fields.remove(control);}
    public void error(Control control,String message) {
        if(firstInvalid==null)firstInvalid=control;
        control.pseudoClassStateChanged(INVALID,true);control.setAccessibleHelp(message);
        Label error=fields.get(control);if(error!=null){error.setText(message);error.setVisible(true);error.setManaged(true);}
    }
    public boolean valid(){return firstInvalid==null;}
    public void focusFirst(){if(firstInvalid!=null){Control target=firstInvalid;Platform.runLater(() -> {
        if(target.getScene()==null||!target.getPseudoClassStates().contains(INVALID))return;
        target.getScene().getRoot().applyCss();target.getScene().getRoot().layout();
        target.requestFocus();
        for(var parent=target.getParent();parent!=null;parent=parent.getParent())if(parent instanceof ScrollPane scroll){
            var content=scroll.getContent();if(content==null)break;
            var bounds=content.sceneToLocal(target.localToScene(target.getBoundsInLocal()));
            double overflow=content.getBoundsInLocal().getHeight()-scroll.getViewportBounds().getHeight();
            if(overflow>0)scroll.setVvalue(Math.max(0,Math.min(1,(bounds.getMinY()-40)/overflow)));break;
        }
    });}}
    /** Matches the persisted DECIMAL(15,2) range without silently rounding. */
    public static BigDecimal amount(String text,String name,boolean allowZero) {
        BigDecimal value;
        if(text==null||text.isBlank())throw new IllegalArgumentException("Enter "+name.toLowerCase(java.util.Locale.ROOT)+".");
        try{value=new BigDecimal(text.strip());}catch(NumberFormatException error){throw new IllegalArgumentException("Enter a valid number.");}
        if(value.signum()<0||(!allowZero&&value.signum()==0))throw new IllegalArgumentException(allowZero?"Must be zero or greater.":"Must be greater than zero.");
        try{value=value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException error){throw new IllegalArgumentException("Use at most two decimal places.");}
        if(value.precision()>15)throw new IllegalArgumentException("Use a value no greater than 9,999,999,999,999.99.");
        return value;
    }
    /** Parse editor text so an invalid typed date cannot reuse an old selected value. */
    public static LocalDate date(DatePicker picker) {
        String text=picker.getEditor().getText();
        if(text==null||text.isBlank())throw new IllegalArgumentException("Choose a date.");
        try{LocalDate date=picker.getConverter().fromString(text.strip());if(date==null)throw new IllegalArgumentException();return date;}
        catch(RuntimeException error){throw new IllegalArgumentException("Enter a valid date or use the calendar.");}
    }
}
