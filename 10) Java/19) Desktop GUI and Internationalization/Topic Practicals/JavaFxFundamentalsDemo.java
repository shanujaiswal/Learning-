/**
 * JavaFxFundamentalsDemo.java
 *
 * Demonstrates, with illustrative JavaFX code:
 *     1. The Application subclass + start(Stage) lifecycle (launch -> init -> start -> stop)
 *     2. The Stage / Scene / Node hierarchy, built programmatically with a scene graph
 *     3. Layout containers: BorderPane (top-level), VBox/HBox (nested), GridPane (a form)
 *     4. Basic controls: Label, TextField, Button, CheckBox, ComboBox, ListView
 *     5. Event handling via setOnAction (JavaFX's equivalent of Swing's ActionListener)
 *     6. Offloading background work with Task, marshaling results back via Platform.runLater
 *     7. CSS-based styling, applied via scene.getStylesheets()
 *
 * Covers Theory chapter:
 *     10) Java/Desktop GUI and Internationalization/Theory/02 JavaFX Fundamentals for Modern Desktop GUIs.md
 *
 * IMPORTANT -- this file is illustrative, NOT guaranteed to compile/run out of the box.
 * Since Java 11, JavaFX is a SEPARATE module (OpenJFX), not bundled in the JDK -- it
 * requires explicit dependencies and, when running from the command line, explicit
 * module-path flags. It will NOT compile with a plain `javac` against a bare JDK the
 * way the Swing/i18n practicals in this folder do.
 *
 * Requires (Maven coordinates):
 *     <dependency>
 *         <groupId>org.openjfx</groupId>
 *         <artifactId>javafx-controls</artifactId>
 *         <version>21.0.2</version>
 *     </dependency>
 *
 * Compile/run (after downloading the OpenJFX SDK matching your OS from
 * https://openjfx.io/ and adjusting the path below):
 *     javac --module-path "C:\path\to\javafx-sdk-21.0.2\lib" --add-modules javafx.controls ^
 *           04_javafx_fundamentals_demo.java
 *     java  --module-path "C:\path\to\javafx-sdk-21.0.2\lib" --add-modules javafx.controls ^
 *           JavaFxFundamentalsDemo
 *   or, more commonly in a real project, via the javafx-maven-plugin:
 *     mvn javafx:run
 */

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * A JavaFX application MUST extend javafx.application.Application and override
 * start(Stage). JavaFX's own bootstrap machinery calls start() automatically on
 * the JavaFX Application Thread after launch(args) initializes the toolkit --
 * the JavaFX equivalent of Swing's SwingUtilities.invokeLater pattern, but
 * structured as a lifecycle method rather than a manually-scheduled Runnable.
 */
public class JavaFxFundamentalsDemo extends Application {

    // Kept as a field purely so the background Task (section 5 below) can
    // append status text to it via Platform.runLater.
    private TextArea outputArea;

    public static void main(String[] args) {
        // Bootstraps the JavaFX runtime; eventually calls start() below on the
        // JavaFX Application Thread. Nothing renders until primaryStage.show()
        // is called inside start().
        launch(args);
    }

    /**
     * init() is an OPTIONAL lifecycle override that runs BEFORE start(), off the
     * JavaFX Application Thread -- a good place for non-UI setup (e.g. loading
     * config, opening a DB connection) that shouldn't block UI startup. Not
     * strictly needed for this demo, shown here purely to document the full
     * lifecycle order the Theory file describes: main() -> launch() -> init()
     * -> start(Stage) -> app runs -> stop().
     */
    @Override
    public void init() {
        System.out.println("init() running off the JavaFX Application Thread -- non-UI setup goes here.");
    }

    @Override
    public void start(Stage primaryStage) {

        // -----------------------------------------------------------------
        // 1) Top-level layout -- BorderPane, JavaFX's BorderLayout equivalent:
        //    five regions (top/bottom/left/right/center), center expands to
        //    fill remaining space.
        // -----------------------------------------------------------------
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // -----------------------------------------------------------------
        // 2) TOP -- an HBox (children stacked horizontally) greeting bar,
        //    parallel to the Swing demo's FlowLayout greeting panel.
        // -----------------------------------------------------------------
        Label nameLabel = new Label("Enter your name:");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Ada");
        Button greetButton = new Button("Greet");

        HBox greetingBar = new HBox(10, nameLabel, nameField, greetButton);   // 10px spacing
        greetingBar.setAlignment(Pos.CENTER_LEFT);
        greetingBar.setPadding(new Insets(0, 0, 10, 0));

        // -----------------------------------------------------------------
        // 3) CENTER -- a TextArea acting as an output log, parallel to the
        //    Swing demo's JTextArea/JScrollPane.
        // -----------------------------------------------------------------
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setPrefRowCount(10);

        // Event handling -- setOnAction is JavaFX's equivalent of Swing's
        // ActionListener; both Button and TextField expose it (TextField
        // fires its action on Enter, same semantic shortcut the Theory file
        // for Swing recommends preferring over a raw key listener).
        Runnable greetAction = () -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String message = name.isEmpty()
                    ? "Please enter a name first."
                    : "Hello, " + name + "! Welcome to JavaFX.";
            outputArea.appendText(message + System.lineSeparator());
        };
        greetButton.setOnAction(e -> greetAction.run());
        nameField.setOnAction(e -> greetAction.run());

        // -----------------------------------------------------------------
        // 4) LEFT -- a GridPane form (JavaFX's more flexible answer to
        //    Swing's GridBagLayout: cells can span rows/columns and are
        //    independently sized).
        // -----------------------------------------------------------------
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(10));

        Label languageLabel = new Label("Favorite language:");
        ComboBox<String> languageCombo = new ComboBox<>();
        languageCombo.getItems().addAll("Java", "Kotlin", "Python", "Rust");
        languageCombo.getSelectionModel().selectFirst();

        Label notifyLabel = new Label("Notify on selection:");
        CheckBox notifyCheckBox = new CheckBox("Log combo-box changes below");

        Label historyLabel = new Label("Recently viewed:");
        ListView<String> historyList = new ListView<>();
        historyList.getItems().addAll("Mechanical Keyboard", "Standing Desk", "USB Microphone");
        historyList.setPrefHeight(90);

        form.add(languageLabel, 0, 0);
        form.add(languageCombo, 1, 0);
        form.add(notifyLabel, 0, 1);
        form.add(notifyCheckBox, 1, 1);
        form.add(historyLabel, 0, 2);
        form.add(historyList, 0, 3, 2, 1);   // spans 2 columns, 1 row -- GridPane cell spanning

        // ComboBox change listener -- JavaFX's observable-property style event
        // wiring (a "listener" on a Property, distinct from but conceptually
        // parallel to Swing's ItemListener).
        languageCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (notifyCheckBox.isSelected()) {
                outputArea.appendText("Language changed to: " + newValue + System.lineSeparator());
            }
        });

        VBox leftPanel = new VBox(form);
        leftPanel.setPadding(new Insets(0, 10, 0, 0));

        // -----------------------------------------------------------------
        // 5) BOTTOM -- a button that runs background work via Task, JavaFX's
        //    rough equivalent of Swing's SwingWorker. doInBackground-style
        //    work happens in call(); UI updates from a background thread
        //    must go through Platform.runLater (or, as shown, a bound
        //    property/Task's own thread-safe state), never touch the scene
        //    graph directly off the FX Application Thread.
        // -----------------------------------------------------------------
        Button loadButton = new Button("Run background task (Task)");
        Label statusLabel = new Label("Status: idle");
        loadButton.setOnAction(e -> {
            loadButton.setDisable(true);
            statusLabel.setText("Status: working...");

            Task<List<String>> loadTask = new Task<>() {
                @Override
                protected List<String> call() throws Exception {
                    // Runs off the FX Application Thread -- safe to "block" here
                    // (simulated with Thread.sleep); a real Task would do network/DB I/O.
                    Thread.sleep(800);
                    return List.of("row-1", "row-2", "row-3");
                }
            };
            loadTask.setOnSucceeded(evt -> {
                // JavaFX marshals onSucceeded back onto the FX Application Thread
                // automatically -- safe to touch the scene graph here, same
                // guarantee Swing's SwingWorker.done() provides.
                List<String> result = loadTask.getValue();
                outputArea.appendText("Background task finished, loaded " + result.size()
                        + " row(s): " + result + System.lineSeparator());
                statusLabel.setText("Status: idle");
                loadButton.setDisable(false);
            });
            loadTask.setOnFailed(evt -> {
                statusLabel.setText("Status: failed");
                outputArea.appendText("Background task failed: "
                        + loadTask.getException() + System.lineSeparator());
                loadButton.setDisable(false);
            });

            // Task implements Runnable -- run it on a plain background thread.
            // A real app would typically use javafx.concurrent.Service to manage
            // restartable Tasks, or an ExecutorService for pooling.
            Thread backgroundThread = new Thread(loadTask, "task-thread");
            backgroundThread.setDaemon(true);
            backgroundThread.start();
        });

        HBox bottomBar = new HBox(10, loadButton, statusLabel);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));

        // -----------------------------------------------------------------
        // Assemble the scene graph -- root BorderPane with nested VBox/HBox/
        // GridPane children, mirroring the Theory file's "Stage -> Scene ->
        // Root Node -> nested Nodes" tree diagram.
        // -----------------------------------------------------------------
        root.setTop(greetingBar);
        root.setLeft(leftPanel);
        root.setCenter(outputArea);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 640, 460);

        // -----------------------------------------------------------------
        // 6) CSS styling -- one of JavaFX's biggest advantages over Swing.
        // In a real project this would be an external styles.css file loaded via
        // getClass().getResource(...); inlined here as a data-style string so
        // this single .java file stays self-contained for illustration purposes.
        // -----------------------------------------------------------------
        greetButton.setStyle("-fx-background-color: #2d6cdf; -fx-text-fill: white; -fx-font-weight: bold;");
        loadButton.setStyle("-fx-background-color: #2d6cdf; -fx-text-fill: white; -fx-font-weight: bold;");
        // A real project would instead do:
        //     scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        // with a styles.css file defining ".button { -fx-background-color: ...; }" selectors.

        primaryStage.setTitle("JavaFX Fundamentals Demo");
        primaryStage.setScene(scene);
        primaryStage.show();   // nothing renders until this is called

        outputArea.appendText("JavaFX Fundamentals Demo ready. Try typing a name and pressing Enter or Greet."
                + System.lineSeparator());
    }

    /**
     * stop() is called on shutdown (window closed, Platform.exit() called, etc.)
     * -- the place to release resources, close connections, stop background
     * threads/services cleanly.
     */
    @Override
    public void stop() {
        System.out.println("stop() called -- release resources here before the JVM exits.");
    }
}

/*
 * NOTE on Platform.runLater (referenced in the Theory file's "Common Gotchas"):
 * this demo's background Task marshals its result back to the FX Application
 * Thread via setOnSucceeded/setOnFailed, which JavaFX already guarantees run ON
 * that thread. If you instead update the UI from inside a raw background
 * Thread/Runnable directly (NOT via a Task's callback), you must wrap that
 * update in Platform.runLater(() -> ...) yourself, e.g.:
 *
 *     new Thread(() -> {
 *         String data = fetchSomethingSlow();
 *         Platform.runLater(() -> outputArea.appendText(data));   // back on the FX thread
 *     }).start();
 *
 * Mutating outputArea (or any Node) directly from that background thread
 * WITHOUT Platform.runLater is the JavaFX equivalent of a Swing EDT violation
 * -- both risk corrupted UI state or exceptions.
 */
