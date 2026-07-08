package com.campus.client.ui;

import com.campus.client.mcp.CampusMcpClient;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * FXML Controller class
 *
 * @author User
 */
public class DashboardPageController implements Initializable {
    
    @FXML
    Label introLabel;
    @FXML
    Button viewBookingsButton, createBookingsButton, viewFacilitiesButton, aiAssistantButton, logoutButton;
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        introLabel.setText("Welcome to your Dashboard, %s".formatted(LoginPageController.fullName));
    }
    
}
