package com.coffeemongers.controller;

import com.coffeemongers.view.WorkspaceRouter;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;

public final class HomeLogoController {
    @FXML private Button homeButton;
    @FXML private void goHome() { WorkspaceRouter.openHome(homeButton); }
}
