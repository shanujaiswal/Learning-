# What JavaFX Is and How It Relates to Swing

--> **JavaFX** is Java's modern GUI toolkit, originally shipped as part of the JDK (Java 8-10), then SPUN OUT as a separate open-source project (from Java 11 onward) maintained under the OpenJFX project -- meaning a modern JavaFX app needs an EXPLICIT dependency added (Maven/Gradle) rather than "just being there" in the JDK the way Swing still is. This split happened as part of a broader effort to modularize the JDK and let JavaFX evolve on its own release cadence.
--> JavaFX was designed from the ground up with things Swing lacks natively: CSS-based styling, built-in animation/transition APIs, hardware-accelerated rendering (via a pipeline called Prism), a richer built-in control set (including charts), and native support for both a programmatic UI-building style AND a declarative XML-based one (FXML, covered below).

```xml
<!-- Maven -- JavaFX must be added explicitly, and per-OS-classifier artifacts are common -->
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>21.0.2</version>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-fxml</artifactId>
    <version>21.0.2</version>
</dependency>
```

# The Scene Graph -- JavaFX's Core Rendering Model

--> JavaFX organizes everything on screen as a **scene graph** -- a TREE of `Node` objects (every visible/interactive element: a button, a label, a shape, a container, an image) rooted at a single top-level node. This is a more general, more uniform model than Swing's component tree -- in JavaFX, EVERYTHING is a `Node` (including shapes, text, images, and effects like drop-shadows), so the same transform/effect/event APIs apply uniformly across UI controls, custom shapes, and media.

```text
Stage (the OS window)
  |
  Scene (one Scene visible in the Stage at a time)
    |
    Root Node (often a layout container, e.g. BorderPane)
      |-- Node (e.g. a Button)
      |-- Node (e.g. a Label)
      |-- Node (a nested layout container)
            |-- Node
            |-- Node
```

--> **Stage / Scene / Node -- the three-level hierarchy**:
  - --> **`Stage`** -- the actual OS-level window (title bar, min/max/close). Every JavaFX app has a PRIMARY stage handed to it at startup, and can open additional stages for secondary windows/dialogs.
  - --> **`Scene`** -- the CONTENT shown inside a stage at a given moment -- essentially "what's currently displayed." A `Stage` can SWAP its `Scene` at runtime (e.g. switching from a login screen to a main dashboard) without creating a new window.
  - --> **`Node`** -- everything else: controls, containers, shapes, text, media. A `Scene` wraps exactly one ROOT `Node` (almost always a layout container), and that root's children (and their children) form the rest of the tree.
--> **Deep Dive -- why "graph" and not "tree"** -- it's typically referred to as a "scene GRAPH" (a term borrowed from broader computer graphics) even though structurally it's a tree in JavaFX's case (each `Node` has exactly one parent) -- the terminology reflects the general graphics-programming lineage of the concept rather than implying cycles.

# Application Lifecycle -- Stage/Scene Bootstrap

--> A JavaFX application extends `javafx.application.Application` and overrides `start(Stage primaryStage)`, which JavaFX calls automatically after its own internal startup/toolkit-init machinery runs -- analogous to, but more structured than, Swing's manual `SwingUtilities.invokeLater(() -> ...)` pattern.

```java
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class HelloFxApp extends Application {

    public static void main(String[] args) {
        launch(args);   // bootstraps the JavaFX runtime, eventually calls start() on the FX Application Thread
    }

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("Enter your name:");
        Button button = new Button("Greet");
        button.setOnAction(e -> label.setText("Hello!"));

        VBox root = new VBox(10, label, button);   // 10px spacing between children
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 400, 200);
        primaryStage.setTitle("Hello JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();                        // nothing renders until this is called
    }

    @Override
    public void stop() {
        // Called on shutdown -- release resources, close DB connections, etc.
    }
}
```

--> **Lifecycle order**: `main()` calls `launch(args)` -> JavaFX runtime initializes -> `init()` (optional override, runs off the FX thread, good for non-UI setup) -> `start(Stage)` (runs ON the JavaFX Application Thread -- the JavaFX equivalent of Swing's EDT) -> app runs -> `stop()` called on shutdown.
--> **The JavaFX Application Thread** is JavaFX's single UI thread, with the same rule as Swing's EDT: all scene-graph reads/mutations must happen on it. Long-running work must run on a background thread (`Task`/`Service`, JavaFX's rough equivalent of `SwingWorker`) and marshal results back via `Platform.runLater(...)`.

# FXML vs Programmatic UI

--> **FXML** is JavaFX's declarative XML format for describing a scene graph -- conceptually similar to Android's XML layouts or HTML -- letting you define WHAT the UI looks like separately from the Java code that controls its BEHAVIOR. A **Controller** class, wired to the FXML via annotations, handles the actual logic.

```xml
<!-- hello-view.fxml -->
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.VBox?>

<VBox xmlns:fx="http://javafx.com/fxml" fx:controller="com.example.HelloController" spacing="10">
    <Label fx:id="greetingLabel" text="Enter your name:" />
    <TextField fx:id="nameField" />
    <Button text="Greet" onAction="#handleGreet" />
</VBox>
```

```java
public class HelloController {
    @FXML private Label greetingLabel;
    @FXML private TextField nameField;

    @FXML
    private void handleGreet() {
        greetingLabel.setText("Hello, " + nameField.getText() + "!");
    }
}
```

```java
// Loading FXML into a Scene
@Override
public void start(Stage stage) throws IOException {
    FXMLLoader loader = new FXMLLoader(getClass().getResource("hello-view.fxml"));
    Scene scene = new Scene(loader.load(), 400, 200);
    stage.setScene(scene);
    stage.show();
}
```

| Aspect | FXML | Programmatic (pure Java) |
|---|---|---|
| Separation of concerns | Strong -- UI structure and controller logic live in separate files | Weak -- structure and behavior are interleaved in one class |
| Tooling | Editable visually in Scene Builder (drag-and-drop) | No visual tool -- pure code |
| Dynamic/conditional UI | Awkward -- FXML is static structure, needs Java to manipulate after load | Natural -- UI can be built with full conditional/loop logic inline |
| Learning curve | An extra file format + `@FXML` wiring conventions to learn | Just Java -- no new syntax |
| Refactoring safety | `fx:id`/`onAction` are STRING-matched to the controller -- typos fail at RUNTIME, not compile time | Compiler catches wiring mistakes immediately |
| Best for | Larger apps, teams with a designer role, visually-heavy screens | Small apps/utilities, highly dynamic UIs, prototypes |

--> **Neither is objectively "correct"** -- many real projects mix both: FXML for mostly-static, complex screens (benefiting from Scene Builder's visual editing and clean separation), and programmatic construction for small utility windows, dynamically-generated UI, or custom controls.
--> **Gotcha -- FXML wiring failures are silent until runtime** -- a `fx:id="nameField"` in the FXML with no matching `@FXML private TextField nameField;` in the controller (or a typo in either) doesn't fail at compile time; it throws a `LoadException` when `FXMLLoader.load()` actually runs, usually with a message that's not immediately obvious to a newcomer -- double-check name matching carefully when a screen mysteriously throws on load.

# JavaFX vs Swing -- Direct Comparison

| Aspect | Swing | JavaFX |
|---|---|---|
| JDK bundling | Ships with every JDK | Separate dependency since Java 11 (OpenJFX) |
| Styling | Custom "Look and Feel" (Java code), harder to theme deeply | CSS stylesheets -- familiar syntax, easy theming |
| Layout description | Java code only | Java code OR declarative FXML |
| Animation | Manual (Timer-driven repaints) | Built-in `Timeline`/`Transition` APIs |
| Rendering | Software-rendered by default | Hardware-accelerated (Prism pipeline) |
| Node model | Separate concepts for components/containers/graphics | Unified `Node` for everything (controls, shapes, media, effects) |
| Media/Web support | None built in (needs third-party libraries) | Built-in `MediaPlayer`, embeddable `WebView` (via a bundled WebKit engine) |
| Charts | None built in (needs third-party libraries like JFreeChart) | Built-in chart controls (`LineChart`, `BarChart`, `PieChart`, etc.) |
| Community/momentum today | Mature, stable, mostly maintenance-mode | Actively developed (OpenJFX), the modern recommended default for new Java desktop apps |

--> **Practical guidance** -- for a genuinely NEW desktop Java app in 2026, JavaFX is the more natural starting point given its active development, CSS theming, and richer built-in control set; Swing remains the pragmatic choice mainly when extending an existing Swing codebase or needing the zero-extra-dependency property.

# Basic Controls and Layouts

--> **Common controls** -- largely parallel to Swing's, with JavaFX naming: `Label`, `TextField`, `TextArea`, `Button`, `CheckBox`, `RadioButton` (+ `ToggleGroup`), `ComboBox<T>`, `ListView<T>`, `TableView<T>`, `Slider`, `ProgressBar`, `DatePicker` (built in, unlike Swing which has no built-in date picker).
--> **Common layout containers** (JavaFX's answer to Swing's layout managers, but each is itself a `Node`/container you nest, rather than a separate "manager" object assigned to a panel):

| Layout Container | Behavior |
|---|---|
| `VBox` | Children stacked vertically |
| `HBox` | Children stacked horizontally |
| `BorderPane` | Five regions: `top`, `bottom`, `left`, `right`, `center` (center expands) -- JavaFX's `BorderLayout` equivalent |
| `GridPane` | Row/column grid, cells can span multiple rows/columns, independently sized (more flexible than Swing's uniform `GridLayout`) |
| `StackPane` | Children stacked directly on top of each other (z-order) -- useful for overlays |
| `FlowPane` | Children flow and wrap, like Swing's `FlowLayout` |
| `AnchorPane` | Children pinned by distance from container edges |

```java
BorderPane root = new BorderPane();
root.setTop(new Label("Header"));
root.setLeft(new Button("Menu"));
root.setCenter(new TextArea());
root.setBottom(new Label("Status: Ready"));

GridPane form = new GridPane();
form.setHgap(8);
form.setVgap(8);
form.add(new Label("Name:"), 0, 0);          // column 0, row 0
form.add(new TextField(), 1, 0);              // column 1, row 0
form.add(new Label("Email:"), 0, 1);
form.add(new TextField(), 1, 1);
GridPane.setHgrow(form.getChildren().get(1), Priority.ALWAYS);   // let the text field grow horizontally
```

--> **CSS styling** -- JavaFX nodes can be styled via external `.css` files using CSS-like syntax (`-fx-` prefixed properties), applied via `scene.getStylesheets().add(...)` or per-node `styleClass`/`id` selectors -- a capability Swing has no native equivalent for.

```css
/* styles.css */
.button {
    -fx-background-color: #2d6cdf;
    -fx-text-fill: white;
    -fx-font-size: 14px;
}
```

```java
scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
```

# Common Gotchas

--> **Forgetting JavaFX needs an explicit dependency (Java 11+)** -- a "class not found: javafx.application.Application" or module-related error on a fresh project almost always means the `javafx-controls`/`javafx-fxml` dependencies (and often a build-plugin like `javafx-maven-plugin` to run it correctly with the module system) weren't added -- this trips up developers used to Swing "just working" out of the JDK.
--> **Mutating the scene graph off the JavaFX Application Thread** -- the same class of bug as Swing's EDT violations; any background thread updating a `Label`/`TextField`/etc. directly (instead of via `Platform.runLater(...)`) risks corrupted UI state or exceptions.
--> **FXML `fx:id`/`onAction` typos failing silently until runtime** -- covered above; always test-load screens early and check the exception message carefully (`LoadException` often nests the REAL cause).
--> **Blocking the FX Application Thread with long-running work** -- freezes the whole UI, exactly like blocking Swing's EDT; use `Task`/`Service` (JavaFX's `SwingWorker` equivalent) for background work, updating UI state via bound properties or `Platform.runLater`.
--> **Mixing up `Stage` and `Scene`** -- beginners sometimes try to create a "new window" by making a new `Stage` when they only needed to SWAP the current stage's `Scene` (e.g. navigating from a login screen to a dashboard within the same window) -- know which one you actually need.

# Best Practices Summary

--> Use FXML + Scene Builder for larger, mostly-static screens with real visual design needs; use programmatic construction for small utilities or highly dynamic UI -- mixing both across a project is normal.
--> Keep controller classes thin -- delegate business logic to separate service classes, mirroring the Controller -> Service layering used in backend code.
--> Style with CSS stylesheets rather than hardcoding colors/fonts in Java -- it's one of JavaFX's biggest advantages over Swing, so use it.
--> Offload any non-trivial work to `Task`/`Service` and never block the JavaFX Application Thread.
--> For new desktop projects with no existing Swing investment, default to JavaFX given its active development and richer built-in feature set.
--> Remember the explicit dependency + module-system considerations that come with JavaFX being separate from the JDK since Java 11 -- budget for that setup, unlike Swing.
