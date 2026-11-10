package com.coffeemongers.controller;

import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

public final class AuthHeroController {
    @FXML private StackPane heroPane;
    @FXML private ImageView heroImage;
    @FXML private void initialize(){heroPane.widthProperty().addListener((o,a,b)->updateCrop());heroPane.heightProperty().addListener((o,a,b)->updateCrop());updateCrop();}
    private void updateCrop(){Image image=heroImage.getImage();double width=heroPane.getWidth(),height=heroPane.getHeight();if(image==null||image.getWidth()<=0||image.getHeight()<=0||width<=0||height<=0)return;double sourceWidth=image.getWidth(),sourceHeight=image.getHeight(),targetRatio=width/height,sourceRatio=sourceWidth/sourceHeight;double x=0,y=0,cropWidth=sourceWidth,cropHeight=sourceHeight;if(sourceRatio>targetRatio){cropWidth=sourceHeight*targetRatio;x=(sourceWidth-cropWidth)/2;}else{cropHeight=sourceWidth/targetRatio;y=(sourceHeight-cropHeight)/2;}heroImage.setViewport(new Rectangle2D(x,y,cropWidth,cropHeight));heroImage.setFitWidth(width);heroImage.setFitHeight(height);}
}
