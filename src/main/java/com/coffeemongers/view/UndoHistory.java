package com.coffeemongers.view;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Keyboard-only undo for explicitly reversible marketplace actions. Text fields retain native undo. */
public final class UndoHistory {
    private static final Map<Stage,Deque<Entry>> HISTORY=new WeakHashMap<>();
    private static final Map<Scene,EventHandler<KeyEvent>> FILTERS=new WeakHashMap<>();
    private UndoHistory() { }
    public static void register(Node source,String description,Runnable undo,Consumer<String> completion){
        if(source.getScene()==null||source.getScene().getWindow()==null)return;
        Stage stage=(Stage)source.getScene().getWindow();Scene scene=source.getScene();
        synchronized(HISTORY){HISTORY.computeIfAbsent(stage,k->new ArrayDeque<>()).push(new Entry(description,undo,completion));}
        synchronized(FILTERS){if(!FILTERS.containsKey(scene)){EventHandler<KeyEvent> filter=event->{if(event.getCode()!=KeyCode.Z||(!event.isMetaDown()&&!event.isControlDown()))return;if(event.getTarget() instanceof TextInputControl)return;undoLast(stage);event.consume();};FILTERS.put(scene,filter);scene.addEventFilter(KeyEvent.KEY_PRESSED,filter);}}
    }
    static void clear(Stage stage){synchronized(HISTORY){HISTORY.remove(stage);}}
    private static void undoLast(Stage stage){Entry entry;synchronized(HISTORY){Deque<Entry> stack=HISTORY.get(stage);entry=stack==null?null:stack.poll();if(stack!=null&&stack.isEmpty())HISTORY.remove(stage);}if(entry==null)return;try{entry.action().run();if(entry.completion()!=null)entry.completion().accept("Undid: "+entry.description());}catch(RuntimeException exception){if(entry.completion()!=null)entry.completion().accept("Undo could not be applied: "+exception.getMessage());}}
    private record Entry(String description,Runnable action,Consumer<String> completion) { }
}
