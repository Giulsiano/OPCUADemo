package serverUI;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;

public class ServerUIController {
    @FXML
    private Shape statusLed;

    @FXML
    private Label serverLabel;

    private boolean isRunning = false;
    private boolean isSampling = false;

    private void startServer (){
        serverLabel.setText("Server is running");
        statusLed.setFill(Paint.valueOf("#00ff00"));
    }

    private void stopServer (){
        serverLabel.setText("Server is not running");
        statusLed.setFill(Paint.valueOf("#ff0000"));
    }
    //Receive message from scene 1
    public void transferMessage (String message) {
        //Display the message
        switch (message){
            case "start":
                startServer();
                break;

            case "stop":
                stopServer();
                break;

            default:
                break;
        }
    }
}