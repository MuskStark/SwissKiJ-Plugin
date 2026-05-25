package plugin.swisskit.hpl;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Stage;
import plugin.swisskit.hpl.ui.HappyLearningUi;

public class HappyLearnDevApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new Group(new HappyLearningUi().getView()), 800, 600);
        stage.setTitle("HappyLearn Dev");
        stage.setScene(scene);
        stage.show();
    }
}
