package clientUI;

import clientLogic.ClientDemo;
import clientLogic.ClientDemoRunner;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class ClientUIController {
    public static final String CLIENT_START = "Start";
    public static final String CLIENT_STOP = "Stop";

    @FXML
    private Button startBtn;
    @FXML
    private TextField txtCurrentServer;
    @FXML
    private TextField txtDataValue;

    private ClientDemoRunner client;
    private AnimationTimer timer;
    private Thread clientThread;

    public void initialize () {
        client = new ClientDemoRunner(new ClientDemo());
        clientThread = new Thread(client, "Client");
        timer = new AnimationTimer(){
            @Override
            public void handle (long l){
                try {
                    String currentServer = ((ClientDemo) client.getClientExample()).getCurrentServerId();
                    String data = ((ClientDemo) client.getClientExample()).getValue();
                    txtCurrentServer.textProperty().setValue(currentServer);
                    txtDataValue.textProperty().setValue(data);
                }
                catch (Exception ignored){}
            }
        };
        startBtn.setText(CLIENT_START);
        startBtn.setOnAction(event -> {
            switch (startBtn.textProperty().get()){
                case CLIENT_START:
                    startBtn.textProperty().setValue(CLIENT_STOP);
                    clientThread.start();
                    timer.start();
                    break;

                case CLIENT_STOP:
                    startBtn.textProperty().setValue(CLIENT_START);
                    clientThread.interrupt();
                    timer.stop();
                    break;

                default:
                    break;
            }
        });
    }
}