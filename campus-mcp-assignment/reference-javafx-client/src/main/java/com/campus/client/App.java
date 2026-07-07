package com.campus.client;

import com.campus.client.llm.AnthropicClient;
import com.campus.client.llm.GeminiClient;
import com.campus.client.llm.LlmClient;
import com.campus.client.llm.OpenAiClient;
import com.campus.client.mcp.CampusMcpClient;
import com.campus.client.rag.RagService;
import com.campus.client.ui.LoginPageController;
import com.campus.client.ui.FacilityInfoController;
//import com.campus.client.rag.RagService_old;
import com.campus.client.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import javafx.scene.Parent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JavaFX host for the reference client. Resolves configuration, connects to the Campus MCP server
 * over HTTP/SSE on a background thread, builds the RAG stack, and hands everything to {@link MainView}.
 *
 * <p>Configuration (all overridable):
 * <ul>
 *   <li>{@code -Dmcp.server.url} or env {@code MCP_SERVER_URL} (default http://localhost:8080)</li>
 *   <li>env {@code ANTHROPIC_API_KEY} (required for the RAG tab)</li>
 *   <li>{@code -Danthropic.model} (default claude-sonnet-4-6)</li>
 * </ul>
 */
public final class App extends Application {
    
    private static Scene rootScene;
    private static String startingFxmlName = "LoginPage";

    private static final Logger log = LoggerFactory.getLogger(App.class); //error logger
    private static final String DEFAULT_URL = "http://localhost:8080";    //server socket (ip address and port)

    //private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-6"; //kinda unimportant
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";          //kinda unimportant
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";     //our default Gemini AI model

    /** Default is anthropic. Change this to suit your need - anthropic, gemini, openai, google
        Remember to set the <PROVIDER>_API_Key value in your environment variable.
        Warning: DO NOT store API_Keys in your source code!
    **/
    private static final String DEFAULT_PROVIDER = "google"; //anthropic  //openai

    private CampusMcpClient mcp; //temporary variable to allow for nulls and actual CampusMcpClient objects (we'll try to keep this null if the connection failed)
    private LlmClient llm;       //temporary variable to allow for nulls and actual LlmClient objects (null if there is no API key or provider found)
//    private MainView view;       //can change out

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        /* Establish connection BEFORE allowing the first controller class to be initialised */
        Thread t = new Thread(this::bootstrap, "mcp-bootstrap"); //!!!ABSOLUTELY NECESSARY, bootstrap is a param-less 
        t.setDaemon(true);
        t.start();
        
        stage.setMinHeight(600);
        stage.setMinWidth(800);
        stage.setTitle("Campus MCP Reference Client");
        try {
            rootScene = new Scene(loadFXML(startingFxmlName), 800, 600);
        } catch (IOException e) {
            System.err.println(String.format("No FXML page called %s, Aborting program!", startingFxmlName));
            System.exit(1);
        }
        stage.setScene(rootScene);
        stage.show();
    }
    
    public static void setRoot(String fxml) {
        try {
            rootScene.setRoot(loadFXML(fxml));
        } catch (IOException pageNotFound) {
            System.err.println(String.format("No FXML file such as \"%s\" was found! Aborting setRoot", fxml));
        } 
        
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }
    
    /**
     *  Bootstrap does the following:
     *  
     *  1. finds the server URL
     *  2. creates the "mcp" object (which acts as the interface of the connection from client to server)
     *  3. literally connects the client to the server, initiating handshake (connect method)
     *  4. builds the LlmClient object (basically gives the LlmClient the "model", "API key", "maxTokens", and acts as the interface to make a connection to the LLM API, and to ask it questions and to get a response (complete method))
     *  5. builds the RagService object (uses the "mcp" object to call get the server's grounding prompts (augmentation) as well as to get the knowledge base (the handbook, faq, facilities, etc))
     *     and uses the "llm" to get an AI generated answer, "ask" returns a RagResult (contains relevant excerpts, systemPrompt and the AI's answer)
     *  6. binds the "mcp" and "rag" to the MainView (basically means it gives the JavaFX UI all accessibilities to the client-server methods and AI asking capabilities)
     *  
     * =================================================================================================================================================================================
     * IMPORTANT INITIALISATIONS IN THEIR RESPECTIVE ORDERS
     * =================================================================================================================================================================================
     * 1. ESTABLISH SERVER CONNECTION
     * └> a. Store the server socket address
     *    b. Create the CampusMcpClient object (store it somewhere all the controller classes can access throughout the App.java's lifetime)
     *    c. Execute the connect() method from the CampusMcpClient object (if connection with the server fails, an unchecked RuntimeException is thrown (not bubbled))
     * 
     * 2. BUILD THE LLM CLIENT (interface for "complete" method)
     * └> a. Get the "provider" from DEFAULT_PROVIDER
     *    b. Get the "model" from DEFAULT_GEMINI_MODEL
     *    c. Get the "key" (api key) from GEMINI_API_KEY (session environment variable, set by "set" in cmd)
     * 
     * 3. BUILD THE RAG SERVICE (interface efor "ask" method)
     * └> a. Create the "rag" object (takes CampusMcpClient object and LlmClient object) (store this in a place that is accessible by all classes)
     * 
     * 
     * 
     */
    
    private void bootstrap() {
        Map<String, String> errorStep = new HashMap<>(Map.of(
                "step" , "",
                "severity" , ""
        ));
        try {
            try { 
                /**
                *   This try block is dedicated to setting up the Client===Server Connection
                *   Any errors (Exception there are wayyyy too many) will be thrown to the outer try-catch
                * 
                */
                errorStep.replace("step", "MCP CLIENT-SERVER CONNECTION");
                errorStep.replace("severity", "fatal");
                
                String url = firstNonBlank(System.getProperty("mcp.server.url"), //!!! ABSOLUTELY NECESSARY, ACTS AS THE URL TO THE SERVER http://localhost:8080
                    System.getenv("MCP_SERVER_URL"), DEFAULT_URL);
                mcp = new CampusMcpClient(url);                                //!!! ABSOLUTELY NECESSARY, ACTS AS THE INTERFACE OF THE CONNECTION TO THE SERVER WITH CLIENT
                var init = mcp.connect();
                //do any server reading here, assuming that it hasn't failed up to this point
                //
            } catch (Exception campusServerConnectionException) {
                /* If an Exception is thrown, it means that the client failed to connect with the server */
                mcp = null;
                throw campusServerConnectionException;
            }
            
            
            
            try {
                /**
                 *  This try-catch block is dedicated to the building and connection of the LlmClient to the LLM API
                 * 
                 */
                errorStep.replace("step", "LLM-CLIENT SETUP/CONNECTION");
                errorStep.replace("severity", "warning");
                
                llm = buildLlmClient(); //!!!ABSOLUTELY NECESSARY, USES THE API KEY and PROVIDER TO CONNECT TO THE LLM API, THEN SERVES AS THE INNER complete INTERFACE
                
            } catch (Exception llmConnectionException) {
                llm = null;
                throw llmConnectionException;
            }
            
            final RagService rag = (llm == null && mcp == null) ? null : new RagService(mcp, llm); //!!! ABSOLUTELY NECESSARY, interface to call "ask" to get an answer from the LLM (underneath, it calls the llm.complete(...) method to get an AI generated response)
            
            /**
             * THIS AREA WILL BE DEDICATED TO BINDING THE MCP AND RAG SERVICES TO ALL THE CONTROLLER CLASSES
             * 
             */
            LoginPageController.bind(mcp);
            FacilityInfoController.bind(mcp);
            
            
            
        } catch (Exception e) {
            /* This catch block is just to catch any and all exceptions, then reports where the error took place*/
            String[] errorMessage = {
                String.format("[%s ERROR @ %s]",errorStep.get("severity").toUpperCase(), errorStep.get("step"))};
            log.error(Arrays.stream(errorMessage).collect(Collectors.joining(System.lineSeparator())));
        }
    }

    /**
     * Builds an LLM client based on the {@code LLM_PROVIDER} setting and the appropriate API-key
     * environment variable. Returns {@code null} (RAG tab disabled) if no key is configured.
     * 
     * 1. establishes "provider" (gemini/google), then proceeds to get the key from the environment variable "GEMINI_API_KEY"
     * 2. Creates the "llm" LLM API interface that is used to call .complete(userPrompt, systemPrompt) to get a generated response from the AI model (inside of RagService object) 
     * 3. returns the completed "llm" object
     * 
     */
    private LlmClient buildLlmClient() {
        String provider = firstNonBlank(DEFAULT_PROVIDER).toLowerCase(); //KEITH's EDIT: Just stopping it from accessing anything else but GEMINI
        int maxTokens = 1024;

        switch (provider) {
            case "anthropic" -> {
                String key = firstNonBlank(System.getProperty("anthropic.apiKey"),
                        System.getenv("ANTHROPIC_API_KEY"), null);
                if (key == null) return null;
                String model = firstNonBlank(System.getProperty("anthropic.model"), DEFAULT_ANTHROPIC_MODEL);
                return new AnthropicClient(key, model, maxTokens);
            }
            case "openai" -> {
                String key = firstNonBlank(System.getProperty("openai.apiKey"),
                        System.getenv("OPENAI_API_KEY"), null);
                if (key == null) return null;
                String model = firstNonBlank(System.getProperty("openai.model"), DEFAULT_OPENAI_MODEL);
                return new OpenAiClient(key, model, maxTokens);
            }
            case "gemini", "google" -> {
                String key = firstNonBlank(System.getProperty("gemini.apiKey"),
                        System.getenv("GEMINI_API_KEY"), System.getenv("GOOGLE_API_KEY"), null);
                if (key == null) return null;
                String model = firstNonBlank(System.getProperty("gemini.model"), DEFAULT_GEMINI_MODEL);
                return new GeminiClient(key, model, maxTokens);
            }
            default -> {
                log.warn("Unknown LLM_PROVIDER '{}'; RAG tab disabled.", provider);
                return null;
            }
        }
    }
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public void stop() {
        FacilityInfoController.shutdownSharedService();

        if (mcp != null) {
            mcp.close();
        }
        Platform.exit();
    }
}
