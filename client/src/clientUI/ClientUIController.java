package clientUI;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import serverUI.ServerUIController;

import java.io.IOException;

public class ClientUIController {
    public static final String START_MESSAGE = "start";
    public static final String STOP_MESSAGE = "stop";

    @FXML
    private Label firstLabel;
    @FXML
    private Button startBtn;
    @FXML
    private TextArea clientLog;

    private boolean isServerRunning = false;
    private ServerUIController serverUICtr;

    private void showServerUI (){
        try {
            //Load second scene
            firstLabel.setText("");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("../../server/src/serverUI/serverUI.fxml"));
            Parent serverRoot = loader.load();

            //Get controller of scene2
            serverUICtr = loader.getController();

            //Show scene 2 in new window
            Stage stage = new Stage();
            stage.setScene(new Scene(serverRoot));
            stage.show();
            stage.setOnCloseRequest(windowEvent -> serverUICtr = null);
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }

    public void initialize () {
        //When button clicked, load window and pass data
        startBtn.setText("Start Server");
        startBtn.setOnAction(event -> {
            if (serverUICtr == null) {
                clientLog.appendText("Loading server UI");
                showServerUI();
            }
            else {
                String message = (isServerRunning) ? STOP_MESSAGE: START_MESSAGE;
                clientLog.appendText(String.format("Sending %s message to server", message));
                sendMessage(message);
            }
        });
    }

    public void transferMessage (String serverName, String message){
        clientLog.appendText(String.format("Server: %s said %s", serverName, message));
        switch (message){
            case "started":
                isServerRunning = true;
                break;

            case "stopped":
                isServerRunning = false;
                break;

            default:
                break;
        }
    }

    private void sendMessage(String message) {
        //Pass whatever data you want. You can have multiple method calls here
        serverUICtr.transferMessage(message);
    }
}