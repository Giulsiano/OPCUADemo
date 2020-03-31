package serverLogic;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import serverUI.ServerUIController;

import java.net.URL;

public class Main extends Application {
    // TODO Verify the GUI isn't a OPCUA Client as it seems to be.
    @Override
    public void start(Stage primaryStage) {
        try {
            URL url = new URL("file://" + System.getProperty("user.dir") + "/serverUI/serverUI.fxml");
            FXMLLoader fxmlLoader = new FXMLLoader(url);
            Parent root = fxmlLoader.load();

            primaryStage.setTitle("Server GUI");
            primaryStage.setScene(new Scene(root, 400, 300));
            ServerUIController controller = fxmlLoader.getController();
            primaryStage.setOnCloseRequest(event -> {
                try {
                    if (controller.isServerRunning()){
                        controller.stopServerSet();
                    }
                }
                catch (Exception e){
                    System.err.println(e.getMessage());
                    e.printStackTrace();
                }
                Platform.exit();
            });
            primaryStage.show();
        }
        catch (Exception e){
            e.printStackTrace();
            System.err.println(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
        }
    }

    public static void main(String[] args) {
        try {
            launch(args);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}
