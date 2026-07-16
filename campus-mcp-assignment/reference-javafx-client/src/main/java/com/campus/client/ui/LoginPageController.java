package com.campus.client.ui;

import com.campus.client.App;

import java.util.*;
import java.io.*;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import com.campus.client.mcp.CampusMcpClient;

//unsure if i need these :(
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import javafx.fxml.Initializable;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;

/*****************************************
*   MODUS OPERANDI
*   1. Validates input
*   2. Verify Student login:
*       2.1 Connects with the MCP server (request for students.txt)
*       2.2 Compiles entries from students.txt into a map or something
*       2.3 Looks through student ids and matches with the student id and password
*       2.4 Invoke setRoot("dashboard")
*   3. Incorrect Login details
*       3.1 display "incorrect login details"
*       3.2 clear the textfields
*       3.3 return to this loginpage
**************************************
*/
public class LoginPageController implements Initializable {
    private static CampusMcpClient mcp;
    private static boolean ran = false;
    public static String fullName;
    public static String studentId;
    
    /*****************************************
    *   FXML event handlers and   
    * 
    * 
    **************************************
    */
    
    @FXML
    TextField studentIdInput, studentPasswordInput;
    @FXML
    Button loginButton;
    @FXML   
    Label errorLabel;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        studentIdInput.setPromptText("eg: S-123");
        studentPasswordInput.setPromptText("eg: xxxxxxxxxxxx");
        
        /**
         *  Check if the connection was actually successful
         *  -------------------------------------------------
         *  If unsuccessful, it will disable the login button and will immediately tell the user that the client failed to connect to the server
         * 
         */
        System.out.println("is MCP null? >> %b\n".formatted(mcp==null)
                + "has bind ran before initialize? >> %b".formatted(ran));
        if (mcp == null) {
            loginButton.setDisable(true);
            setErrorLabel("APPLICATION CLOSED\nClient not connected to server, please ensure that the server is running!");
            return;
        }
        
        ExecutorService studentDataStoreWorker = Executors.newSingleThreadExecutor((runnable) -> { //"runnable" is the runnable object that is passed (using submit(runnable))
            Thread thread = new Thread(runnable, "student-datastore-worker");
            thread.setDaemon(true);
            return thread;
        });
        
        //just needed to do this once, uncomment this code to get the student data row back
        
//        studentDataStoreWorker.submit(() -> {
//            System.out.println(mcp.callTool("add_new_student", Map.of(
//                "password", "12345",
//                "fname", "Keith",
//                "lname", "Chan"
//            )));
//            System.out.println(mcp.callTool("add_new_student", Map.of(
//                "password", "12345",
//                "fname", "Keith",
//                "lname", "Chan"
//            )));
//            
//        });
            
        
        
/*======================================================================================================================================*
 *  Setting Login Button's action, will contain the entirety of the input validation and 
 *  the getting of the student data, as well as the password and id matching (student account verification)
 *======================================================================================================================================*/
        loginButton.setOnAction(e -> {
            /**************************** Input Validator ****************************/
            List<String> errorMessage = new ArrayList<>();
            Supplier<String> idSupplier = (studentIdInput.getText().isEmpty()) ? 
                    () -> {
                        errorMessage.add("student id");
                        return null;
                    } 
                    : 
                    () -> {
                        return studentIdInput.getText();
                    };
            String studentIdStr = idSupplier.get();
            Supplier<String> passwordSupplier = (studentPasswordInput.getText().isEmpty()) ?
                    () -> {
                        errorMessage.add("student account password");
                        return null;
                    }
                    :
                    () -> {
                        return studentPasswordInput.getText();
                    };
            String studentPasswordStr = passwordSupplier.get();
            
            if (studentIdStr == null || studentPasswordStr == null) {
                setErrorLabel("Please insert your " + String.join(" and ", errorMessage));
                return;
            }
            
            
            
            /**************************** Student Login Detail Verifier ****************************/
            /* create daemon (background) thread, adding a lambda function where a Thread object is created and returned in allows the daemon to close automatically */
            /**
             *  IMPORTANT TO NOTE:
             *  
             *  Anything that is mcp or server related, like callTools, or readResources must be place into a callable that is ran by a daemon thread
             *  This is to prevent the application from hanging if the main application has to literally wait for a response from the server
             *  ( <*> doesn't cut out the actual waiting time tho, just makes sure that the user can still interact with the UI whilst waiting for a response)
             */
            
            /* The entirety of the verification process will occur here (within the daemon) */
            studentDataStoreWorker.submit(() -> {
                try { //try-catch statement in the case that something in the mcp fails midway through
                    /*First order of business is to see if the student id even exists*/
                    String studentData = mcp.callTool("get_student_data", Map.of("id", studentIdStr.toUpperCase()));
                    if (!studentData.contains("|")) { //none data store structure format (containing '|') means that there's no student found
                        
                        Platform.runLater(() -> {setErrorLabel("Incorrect student id or password!");});
                        return;
                    }
                    
                    String[] tmpTable = studentData.split("\\s*\\|\\s*");
                    Map<String, String> dataTable = new HashMap<>(Map.of(
                            "id", tmpTable[0],
                            "password", tmpTable[1],
                            "fname", tmpTable[2],
                            "mname", tmpTable[3],
                            "lname", tmpTable[4]
                    ));
                    
                    if (!studentPasswordStr.equals(dataTable.get("password"))) {
                        Platform.runLater(() -> {setErrorLabel("Incorrect student id or password!");});
                        return;
                    }
                    
                    //into this point, the login is successful
                    //
                    studentId = studentIdInput.getText();
                    String[] fullNameInParts = {dataTable.get("fname"), dataTable.get("mname"), dataTable.get("lname")};
                    fullName = (dataTable.get("mname").isBlank()) ? String.join(" ", List.of(fullNameInParts[0], fullNameInParts[2])) : 
                            String.join(" ", List.of(fullNameInParts[0], fullNameInParts[1], fullNameInParts[2]));
                    App.setRoot("DashboardPage");
                    
                } catch (Exception mcpRequestException) {
                    Platform.runLater(() -> {setErrorLabel("Something went wrong with the verification request");});
                    
                }
            });
            
        });
        
    }
    
    public void setErrorLabel(String message) {
        errorLabel.setText(message);
        
    }
    
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///LOGIC
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
//    public boolean verifyLogin() { //returns true if login was successful
//        //Validates input
//        //Verifies student login
//        //returns true or based on its status
//        
//        //NOTE: Has the power to invoke error screens
//        
//        String id = "";
//        String password = "";
//        
//        if (studentIdInput.getText().isEmpty()) {
//            //display "missing student id"
//            loginFailedScreen("Please input your Taylor's Student ID");
//        } else if (studentPasswordInput.getText().isEmpty()) {
//            loginFailedScreen("Please input your Student Account Password");
//        } else {
//            id = studentIdInput.getText().toLowerCase();
//            password = studentPasswordInput.getText().toLowerCase();
//        }
//            
//        Map<String, String> studentIdsAndPasswords = new HashMap<>(); //get the MCP server student details, and store them here
//        
//        String[] studentIds = (String[]) studentIdsAndPasswords.keySet().toArray();
//        for (int i = 0; i < studentIdsAndPasswords.size(); i++) {
//            if (id.equals(studentIds[i].toLowerCase()) && password.equals(studentIdsAndPasswords.get(studentIds[i]))) {
//                return true;
//            }
//        }
//        
//        loginFailedScreen("Incorrect Student ID or Password");
//        return false;
//    }
    
    /**
     * Simply gets the reference of the mcp (after connection during runtime)
     * 
     */
    public static void bind(CampusMcpClient mcp) {
        LoginPageController.mcp = mcp;
        ran = true;
    }
    
    
}
