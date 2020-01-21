package clientUI;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import serverUI.ServerUIController;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ClientUIController implements Initializable {
    @FXML
    private Label firstLabel;
    @FXML
    private Button startBtn;

    private int coin;
    ServerUIController serverUICtr;

    public void changeLabel (){
        firstLabel.setText(((this.coin & 0x1) == 0) ? "ciaone" : "hello world");
        this.coin++;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //When button clicked, load window and pass data
        startBtn.setOnAction(event -> {
            loadSceneAndSendMessage();
        });
    }

    private void loadSceneAndSendMessage() {
        try {
            //Load second scene
            FXMLLoader loader = new FXMLLoader(getClass().getResource("../serverUI/serverUI.fxml"));
            Parent root = loader.load();

            //Get controller of scene2
            serverUICtr = loader.getController();
            //Pass whatever data you want. You can have multiple method calls here
            serverUICtr.transferMessage(((this.coin & 0x1) == 0) ? "start" : "stop");

            //Show scene 2 in new window
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }
}