package com.campus.client.ui;

import com.campus.client.mcp.CampusMcpClient;
import com.campus.client.App;
import com.campus.client.ui.LoginPageController;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.Initializable;

import java.lang.Exception;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.*;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
        
        
public class ViewBookingsPageController implements Initializable {
    private static CampusMcpClient mcp;
    private static List<Map<String, String>> allBookings = new ArrayList<>();
    private static List<BookingBlock> allBookingBlocks = new ArrayList<>();
    
    ExecutorService bookingDataStoreWorker = Executors.newSingleThreadExecutor((runnable) -> { //"runnable" is the runnable object that is passed (using submit(runnable))
        Thread thread = new Thread(runnable, "bookings-datastore-worker");
        thread.setDaemon(true);
        return thread;
    });
    
    @FXML
    Button backButton, createNewBookingButton;
    @FXML
    Label alertLabel;
    @FXML
    Pane bookingsVBox;
    
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        
        BookingBlock.parentWidthHeight = new Double[]{bookingsVBox.getWidth(), bookingsVBox.getHeight()};
        
        backButton.setOnAction(e -> {
            App.setRoot("DashboardPage");
        });
        
        createNewBookingButton.setOnAction(e -> {
           App.setRoot("BookResourcePage");
        });
        
        initialiseBookingBlocks();
    }
    
    
    
    /**
     *  Called everytime the page is reloaded...
     *  1. clears both the allBookings and allBookingBlocks lists
     *  2. uses the callTool method to populate the allBookings list with bookings under the current logged in student
     *  3. populates the allBookingBlocks list with the allBookings items (by creating new BookingBlock objects)
     *  4. finally, adds the allBookingBlocks' list's BookingBlock's UI into the scene
     * 
     */
    private void initialiseBookingBlocks() {
        allBookings.clear();
        allBookingBlocks.clear();
        
        //populating the allBookings list (worker thread)
        bookingDataStoreWorker.submit(() -> {
            try {
                String rawBookingsText = mcp.callTool("get_student_room_bookings", Map.of("student_id", LoginPageController.studentId.toUpperCase()));
                String[] rawBookingsParts = rawBookingsText.split("\\n");
                
                for (String rawBookingPart : rawBookingsParts) {
                    String[] parts = rawBookingPart.split("\\s*|\\s*");
                    //mapping the split parts into its easier to access map objects
                    allBookings.add(Map.of(
                            "booking_id", parts[0],
                            "room_id", parts[1],
                            "start_date", parts[2],
                            "start_time", parts[3],
                            "end_time", parts[4],
                            "created_datetime", parts[5]
                    ));
                }
                
                System.out.println("\n\n" + String.join("\n", rawBookingsParts));
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
        //populate the allBookingBlocks list (main JavaFX application thread)
        for (Map<String, String> bookingInfo : allBookings) {
            allBookingBlocks.add(new BookingBlock(
                    bookingInfo.get("booking_id"),
                    bookingInfo.get("room_id"),
                    bookingInfo.get("start_date"),
                    bookingInfo.get("start_time"),
                    bookingInfo.get("end_time"),
                    bookingInfo.get("created_datetime")
            ));
        }
        
        //add the BookingBlocks to the scene in javafx (accessing BookingBlock's instance UI member)
        for (BookingBlock bb : allBookingBlocks) {
            bookingsVBox.getChildren().add(bb.UI);
        }
        
    }
    
    public static void bind(CampusMcpClient mcp) {
        ViewBookingsPageController.mcp = mcp;
    }
    
}

//Crucially used to store information about the booking itself that the block represents
class BookingBlock {
    public static Double[] parentWidthHeight;
    
    public String bookingId;
    public String roomId;
    public LocalDate startDate;
    public LocalTime startTime;
    public LocalTime endTime;
    public LocalDateTime creationDateTime;
    public Pane UI;
    
    //hiding the default ctor
    private BookingBlock() {};
    
    
    public BookingBlock(String bookingId, String roomId, Object...dateTimeInfo) {
        if (dateTimeInfo.length != 3) {
            try {
                throw new Error("DateTimeInfo must contain the startdate, starttime, and endtime");
            } catch (Error e) {
                e.printStackTrace();
            }
        }
        this.bookingId = bookingId;
        this.roomId = roomId;
        this.startDate = (LocalDate) dateTimeInfo[0];
        this.startTime = (LocalTime) dateTimeInfo[1];
        this.endTime = (LocalTime) dateTimeInfo[2];
        this.creationDateTime = (LocalDateTime) dateTimeInfo[3];
        this.UI = createBbUi();
    }
    
    /**
    *   Creates new blocks each containing information about each booking under the student
    *   adds the new bookings to the allBookingBlocks list
    * 
    *   TESTING
    */
    private Pane createBbUi() {
        HBox mainFrame = new HBox();
        Button deleteBookingButton = new Button("Delete Booking");
        deleteBookingButton.setOnAction(e -> this.deleteBbUi());
        mainFrame.setMinSize(parentWidthHeight[0], 30);
        mainFrame.getChildren().add(deleteBookingButton);
        
        
        
        return mainFrame;
    }
    
    /**
     *  Used as the event handler's method reference to be executed upon clicking on the "Delete Booking" button on particular booking block
     *  Uses the booking id (# ref) to call the CampusTool method to delete the entry 
     *  Finally, reloads the page
     */
    private void deleteBbUi() {
        
    }
    
}