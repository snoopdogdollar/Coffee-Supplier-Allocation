package com.coffeemongers.controller;

import com.coffeemongers.service.AppServices;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class EnvironmentStatusController {
    @FXML private Label environmentLabel;
    @FXML private void initialize() { environmentLabel.setText(AppServices.environmentStatus()); }
}
