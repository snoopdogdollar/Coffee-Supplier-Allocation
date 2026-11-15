package com.coffeemongers.view;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.beans.value.ChangeListener;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** One scene per window; navigation swaps content while Back restores the prior view. */
public final class SceneNavigator {
    private static final Map<Stage, Deque<Page>> HISTORY = new WeakHashMap<>();
    private SceneNavigator() { }

    public static void show(Node source,String resource,String title){show(source,resource,title,null);}
    public static void show(Node source,String resource,String title,Consumer<Object> configureController){
        Stage stage=stageFor(source);
        if(stage.getScene()!=null)synchronized(HISTORY){HISTORY.computeIfAbsent(stage,key->new ArrayDeque<>()).push(new Page(stage.getScene().getRoot(),stage.getTitle()));}
        load(stage,resource,title,configureController);
    }
    /** Replace the current workspace route without adding a misleading Back entry. */
    public static void replace(Node source,String resource,String title){load(stageFor(source),resource,title,null);}
    /** Start a new authenticated/logged-out flow, discarding screens from the old user. */
    public static void clearAndShow(Node source,String resource,String title){Stage stage=stageFor(source);synchronized(HISTORY){HISTORY.remove(stage);}UndoHistory.clear(stage);load(stage,resource,title,null);}
    public static void back(Node source,String fallbackResource,String fallbackTitle){
        Stage stage=stageFor(source);Page page=null;
        synchronized(HISTORY){Deque<Page> stack=HISTORY.get(stage);if(stack!=null&&!stack.isEmpty()){page=stack.pop();if(stack.isEmpty())HISTORY.remove(stage);}}
        if(page==null){load(stage,fallbackResource,fallbackTitle,null);return;}
        display(stage,page.root(),page.title());
    }
    public static boolean canGoBack(Stage stage){synchronized(HISTORY){Deque<Page> stack=HISTORY.get(stage);return stack!=null&&!stack.isEmpty();}}
    /** Runs on first display and when a cached root returns to the existing scene. */
    public static void onShown(Node root,Runnable action){
        ChangeListener<Window> windowListener=(property,previous,window) -> {if(window!=null)action.run();};
        root.sceneProperty().addListener((property,previous,scene) -> {
            if(previous!=null)previous.windowProperty().removeListener(windowListener);
            if(scene!=null){scene.windowProperty().addListener(windowListener);if(scene.getWindow()!=null)action.run();}
        });
        if(root.getScene()!=null){root.getScene().windowProperty().addListener(windowListener);if(root.getScene().getWindow()!=null)action.run();}
    }
    private static Stage stageFor(Node source){if(source==null||source.getScene()==null||source.getScene().getWindow()==null)throw new IllegalStateException("Navigation requires a node attached to a window.");return (Stage)source.getScene().getWindow();}
    private static void load(Stage stage,String resource,String title,Consumer<Object> configureController){
        try{
            var url=SceneNavigator.class.getResource(resource);if(url==null)throw new IllegalStateException("View not found: "+resource);
            FXMLLoader loader=new FXMLLoader(url);Parent root=loader.load();if(configureController!=null)configureController.accept(loader.getController());
            display(stage,root,title);
        }catch(Exception exception){throw new IllegalStateException("Could not open "+title,exception);}
    }
    private static void display(Stage stage,Parent root,String title){
        if(stage.getScene()==null){
            Scene scene=new Scene(root,1280,800);
            var css=SceneNavigator.class.getResource("/com/coffeemongers/view/buyer-dashboard.css");
            if(css!=null)scene.getStylesheets().add(css.toExternalForm());
            stage.setScene(scene);
        }else stage.getScene().setRoot(root);
        stage.setTitle(title);
    }
    private record Page(Parent root,String title){ }
}
