/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package com.campus.client.ui;

import com.campus.client.mcp.CampusMcpClient;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;

import java.lang.Exception;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FXML Controller class
 *
 * @author User
 */
public class ViewBookingsPageController implements Initializable {
    private static CampusMcpClient mcp;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ExecutorService studentDataStoreWorker = Executors.newSingleThreadExecutor((runnable) -> { //"runnable" is the runnable object that is passed (using submit(runnable))
            Thread thread = new Thread(runnable, "bookings-datastore-worker");
            thread.setDaemon(true);
            return thread;
        });
        
        studentDataStoreWorker.submit(() -> {
            try {
                String bookingDataBasedOnStudentId = mcp.callTool("get_student_room_bookings", Map.of(
                        "student_id", "03456789".toUpperCase()
                ));
                System.out.println("\n\n" + bookingDataBasedOnStudentId + "\n\n");
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("\n\n" + "something wrong in the backend" + "\n\n");
            }
        });
        
    }
    
    public static void bind(CampusMcpClient mcp) {
        ViewBookingsPageController.mcp = mcp;
    }
    
}
