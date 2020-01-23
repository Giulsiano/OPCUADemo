package serverUI;

import clientUI.ClientUIController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;

import java.io.IOException;

public class ServerUIController {
    @FXML
    private Shape statusLed;

    @FXML
    private Label serverLabel;

    private ClientUIController clientController;

    private boolean isRunning = false;
    private boolean isSampling = false;

    private String serverName = "OPCUAServer";

    public void initialize() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("../clientUI/clientUI.fxml"));
            loader.load();
            clientController = loader.getController();
        }
        catch (IOException e){
            System.err.println("Error loading FXML file: " + e);
        }
    }

    private void startServer (){
        if (!isRunning) {
            isRunning = true;
            serverLabel.setText("Server is running");
            statusLed.setFill(Paint.valueOf("#00ff00"));
        }
    }

    private void stopServer (){
        if (isRunning){
            isRunning = false;
            serverLabel.setText("Server is not running");
            statusLed.setFill(Paint.valueOf("#ff0000"));
        }
    }

    //Receive message from client
    public void transferMessage (String message) {
        //Display the message
        switch (message){
            case "start":
                startServer();
                clientController.transferMessage(serverName, "started");
                break;

            case "stop":
                stopServer();
                clientController.transferMessage(serverName, "stopped");
                break;

            default:
                break;
        }
    }

    // Getters and setters
    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}