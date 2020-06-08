package sample;

        import javafx.fxml.FXML;
        import javafx.fxml.FXMLLoader;
        import javafx.scene.Parent;
        import javafx.scene.Scene;
        import javafx.stage.Stage;

        import java.awt.event.ActionEvent;
        import java.io.IOException;

public class MainPageController
{

    @FXML
    private void handleButtonAction(javafx.event.ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("fxml/connexion.fxml"));
        Parent parent =  loader.load();
        Scene mainScene = new Scene(parent);

        Stage window = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        window.setScene(mainScene);
        window.show();
    }
}
