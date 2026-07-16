package com.campus.client.ui;

import com.campus.client.App;
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
        
        viewBookingsButton.setOnAction(e -> {
            App.setRoot("ViewBookingsPage");
        });
        
        createBookingsButton.setOnAction(e -> {
            App.setRoot("BookResourcePage");
        });
        
        viewFacilitiesButton.setOnAction(e -> {
            App.setRoot("FacilityInfoPage");
        });
        
        aiAssistantButton.setOnAction(e -> {
            App.setRoot("");
        });
        
        logoutButton.setOnAction(e -> {
            App.setRoot("LoginPage");
        });
    }
    
}
