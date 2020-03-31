package serverUI;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import serverLogic.RedundantServer;
import serverLogic.RedundantServerSet;

import java.net.URL;
import java.util.ResourceBundle;

public class ServerUIController implements Initializable{
    public final String NO_DATA_VALUE = "No data!";

    private RedundantServerSet serverSet;
    private boolean setIsRunning = false;

    @FXML
    private Label lblMasterServerID;

    @FXML
    private Label lblSecondServerID;

    @FXML
    private TextField txtDataValue;

    @FXML
    private Shape statusLedMain;

    @FXML
    private Shape statusLedSecondary;

    public void startServerSet (){
        new Thread(() -> {
            try {
                serverSet = new RedundantServerSet(3);
                setIsRunning = true;
                serverSet.run();
            }
            catch (Exception e){
                Alert exceptionAlert = new Alert(Alert.AlertType.ERROR);
                exceptionAlert.setTitle("Exception " + e);
                exceptionAlert.setContentText("An error happens running the redundant server set.\n" +
                        "Message: " + e.getLocalizedMessage());
                exceptionAlert.setHeaderText("Error occourred");
            }
        }).start();
    }

    public void stopServerSet (){
        setIsRunning = false;
        serverSet.shutdown();
    }

    @Override
    public void initialize (URL url, ResourceBundle resourceBundle){
        txtDataValue.setText(NO_DATA_VALUE);
        new AnimationTimer(){
            @Override
            public void handle (long l){
                updateGUI();
            }
        }.start();
    }

    private Paint getServerStateColor (ServerState state){
        switch (state){
            case Running:
                return Paint.valueOf("#00ee00");

            case Failed:
                return Paint.valueOf("#ee0000");

            case Suspended:
                return Paint.valueOf("0000ee");

            case Shutdown:
                return Paint.valueOf("#eeeeff");

            case NoConfiguration:
            case Test:
            case CommunicationFault:
            case Unknown:
            default:
                return Paint.valueOf("#000000");
        }
    }

    public void updateGUI(){
        if (serverSet != null){
            RedundantServer master = serverSet.getCurrentServer();
            RedundantServer secondary = serverSet.getCurrentClient();
            txtDataValue.setText(master.getAnalogValue().toString());
            lblMasterServerID.textProperty().setValue(master.getServerId());
            lblSecondServerID.textProperty().setValue(secondary.getServerId());
            statusLedMain.fillProperty().setValue(getServerStateColor(master.getServerState()));
            statusLedSecondary.fillProperty().setValue(getServerStateColor(secondary.getServerState()));
        }
    }

    public boolean isServerRunning (){
        return setIsRunning;
    }
}