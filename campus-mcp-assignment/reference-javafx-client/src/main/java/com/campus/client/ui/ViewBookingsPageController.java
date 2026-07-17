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
import javafx.application.Platform;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
        
        
public class ViewBookingsPageController implements Initializable {
    public static CampusMcpClient mcp;
    private static List<Map<String, String>> allBookings = new ArrayList<>();
    private static List<BookingBlock> allBookingBlocks = new ArrayList<>();
    
    public static ExecutorService bookingDataStoreWorker = Executors.newSingleThreadExecutor((runnable) -> { //"runnable" is the runnable object that is passed (using submit(runnable))
        Thread thread = new Thread(runnable, "bookings-datastore-worker");
        thread.setDaemon(true);
        return thread;
    });
    
    @FXML
    Button backButton, createNewBookingButton;
    @FXML
    Label alertLabel;
    @FXML
    VBox bookingsVBox;
    
    
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
                //The raw string that the CampusTool method returns (has \n delimiters)
                String rawBookingsText = mcp.callTool("get_student_room_bookings", Map.of("student_id", LoginPageController.studentId.toUpperCase()));
                //splitting the raw text into entries (delimited by " | ")
                String[] rawBookingsParts = rawBookingsText.split("\\n");
                
//                System.out.println("\n\n" + String.join("\n", rawBookingsParts) + "\n\n");
                
                for (String rawBookingPart : rawBookingsParts) {
                    String[] parts = rawBookingPart.split("\\s*\\|\\s*");
                    //mapping the split parts into its easier to access map objects
                    allBookings.add(Map.of(
                            "booking_id", parts[0],
                            "room_id", parts[3],
                            "start_date", parts[2],
                            "start_time", parts[5],
                            "end_time", parts[1],
                            "created_datetime", parts[6]
                    ));
                }
                
//                System.out.println("\n\n" + String.join("\n", rawBookingsParts));
                
                
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
                
//                System.out.println("\n\n" + allBookings + "\n\n");
                
                Platform.runLater(() -> {
                    //add the BookingBlocks to the scene in javafx (accessing BookingBlock's instance UI member)
                    for (BookingBlock bb : allBookingBlocks) {
                        bookingsVBox.getChildren().add(bb.UI);
                    }
                });
                
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        
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
    public String startDate;
    public String startTime;
    public String endTime;
    public String creationDateTime;
    public Pane UI;
    
    //hiding the default ctor
    private BookingBlock() {};
    
    
    public BookingBlock(String bookingId, String roomId, String...dateTimeInfo) {
        if (dateTimeInfo.length != 4) {
            try {
                throw new Error("DateTimeInfo must contain the startdate, starttime, endtime, creationdatetime");
            } catch (Error e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        this.bookingId = bookingId;
        this.roomId = roomId;
        this.startDate = dateTimeInfo[0];
        this.startTime = dateTimeInfo[1];
        this.endTime = dateTimeInfo[2];
        this.creationDateTime = dateTimeInfo[3];
        this.UI = createBbUi();
    }
    
    /**
    *   Creates new blocks each containing information about each booking under the student
    *   adds the new bookings to the allBookingBlocks list
    * 
    *   TESTING
    */
    private Pane createBbUi() {
        // 1. Root Container (Top-to-Bottom flow)
        VBox mainFrame = new VBox();
        mainFrame.setMaxWidth(Double.MAX_VALUE);  // Allow full width expansion

        // ==========================================
        // 2. TOP SECTION (Darker Green Background)
        // ==========================================
        HBox topSection = new HBox();
        topSection.setMaxWidth(Double.MAX_VALUE);  // Fill parent width
        topSection.setStyle("-fx-background-color: #A9D18E; -fx-background-radius: 15 15 0 0; -fx-padding: 20;");
        topSection.setSpacing(20);

        // Top Left Column
        VBox topLeft = new VBox(5); 
        Label lblLeft1 = new Label("Booking ID : %s".formatted(this.bookingId));
        Label lblLeft2 = new Label("Room ID    : %s".formatted(this.roomId));
        Label lblLeft3 = new Label();

        // Top Right Column
        VBox topRight = new VBox(5);
        Label lblRight1 = new Label("Start Date : %s".formatted(this.startDate));
        Label lblRight2 = new Label("Start Time : %s".formatted(this.startTime));
        Label lblRight3 = new Label("End Time   : %s".formatted(this.endTime));

        // Make all top text bold and larger to match the image
        String topTextStyle = "-fx-font-weight: bold; -fx-font-size: 15px;";
        lblLeft1.setStyle(topTextStyle); lblLeft2.setStyle(topTextStyle); lblLeft3.setStyle(topTextStyle);
        lblRight1.setStyle(topTextStyle); lblRight2.setStyle(topTextStyle); lblRight3.setStyle(topTextStyle);

        topLeft.getChildren().addAll(lblLeft1, lblLeft2, lblLeft3);
        topRight.getChildren().addAll(lblRight1, lblRight2, lblRight3);

        // Force columns to split space exactly 50/50
        HBox.setHgrow(topLeft, Priority.ALWAYS);
        HBox.setHgrow(topRight, Priority.ALWAYS);
        topLeft.setMaxWidth(Double.MAX_VALUE);
        topRight.setMaxWidth(Double.MAX_VALUE);

        topSection.getChildren().addAll(topLeft, topRight);

        // ==========================================
        // 3. BOTTOM SECTION (Lighter Green Background)
        // ==========================================
        HBox bottomSection = new HBox();
        bottomSection.setMaxWidth(Double.MAX_VALUE);  // Fill parent width
        bottomSection.setStyle("-fx-background-color: #C5E0B4; -fx-background-radius: 0 0 15 15; -fx-padding: 15 20 15 20;");
        bottomSection.setAlignment(Pos.CENTER_LEFT);

        // Bottom Left Column (Contains Delete Button)
        HBox bottomLeft = new HBox();
        Button deleteBookingButton = new Button("DELETE");
        deleteBookingButton.setStyle("-fx-background-color: #C00000; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 12; -fx-padding: 8 20 8 20;");
        deleteBookingButton.setOnAction(e -> this.deleteBbUi());
        bottomLeft.getChildren().add(deleteBookingButton);

        // Bottom Right Column (Contains Text 4)
        HBox bottomRight = new HBox();
        bottomRight.setAlignment(Pos.CENTER_LEFT);
        Label lblBottomRight = new Label("Created On : %s".formatted(String.join(", ", this.creationDateTime.split("T"))));
        lblBottomRight.setStyle("-fx-font-size: 14px;");
        bottomRight.getChildren().add(lblBottomRight);

        // Force bottom columns to split 50/50 so they perfectly align with the top columns
        HBox.setHgrow(bottomLeft, Priority.ALWAYS);
        HBox.setHgrow(bottomRight, Priority.ALWAYS);
        bottomLeft.setMaxWidth(Double.MAX_VALUE);
        bottomRight.setMaxWidth(Double.MAX_VALUE);

        bottomSection.getChildren().addAll(bottomLeft, bottomRight);

        // ==========================================
        // 4. ASSEMBLE
        // ==========================================
        mainFrame.getChildren().addAll(topSection, bottomSection);

        return mainFrame;
    }
    
    /**
     *  Used as the event handler's method reference to be executed upon clicking on the "Delete Booking" button on particular booking block
     *  Uses the booking id (# ref) to call the CampusTool method to delete the entry 
     *  Finally, reloads the page
     */
    private void deleteBbUi() {
        ViewBookingsPageController.bookingDataStoreWorker.submit(() -> {
            System.out.println("\n\n" + ViewBookingsPageController.mcp.callTool("cancel_booking", Map.of("booking_id", this.bookingId.toUpperCase())) + "\n\n");
            App.setRoot("ViewBookingsPage");
        });
    } 
    
}