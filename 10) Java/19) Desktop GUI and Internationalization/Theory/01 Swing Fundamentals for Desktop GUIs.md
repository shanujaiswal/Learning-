# What Swing Is and Where It Fits

--> **Swing** is Java's original mature GUI toolkit, part of the JDK since Java 1.2 (1998), built on top of the lower-level **AWT (Abstract Window Toolkit)**. Unlike AWT, which delegates rendering to the underlying OS's native widgets ("heavyweight" components), Swing components are **lightweight** -- Swing paints its own pixels for buttons, text fields, menus, etc., rather than asking the OS to draw a native one. This is WHY a Swing app looks IDENTICAL across Windows/macOS/Linux by default (it's drawing itself, not delegating), and also why it can be skinned with custom "Look and Feel" implementations.
--> Swing lives in the `javax.swing` package (with underlying event/graphics primitives still in `java.awt`), and has shipped in every JDK since -- no separate dependency needed to use it, which is one reason it remains common in legacy enterprise tooling and quick internal utilities even decades later.

# JFrame, JPanel, and Basic Components

--> **`JFrame`** is a top-level WINDOW -- it has a title bar, min/max/close buttons, and is the root container every Swing desktop app needs at least one of. It's the Swing equivalent of "the application window."
--> **`JPanel`** is a lightweight, invisible-by-default CONTAINER used to group other components together and apply a layout to just that sub-group -- panels nest inside frames (and inside other panels) to build up complex UIs from smaller, independently-laid-out pieces.

```java
import javax.swing.*;
import java.awt.*;

public class HelloSwingApp {
    public static void main(String[] args) {
        // Swing is NOT thread-safe -- all UI construction/mutation must happen on the
        // Event Dispatch Thread (EDT), never directly on main(). SwingUtilities.invokeLater
        // schedules the Runnable to run ON the EDT.
        SwingUtilities.invokeLater(HelloSwingApp::createAndShowGui);
    }

    private static void createAndShowGui() {
        JFrame frame = new JFrame("Hello Swing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);   // closing the window ends the app
        frame.setSize(400, 200);

        JPanel panel = new JPanel();                 // default layout: FlowLayout
        JLabel label = new JLabel("Enter your name:");
        JTextField textField = new JTextField(15);    // 15-column-wide text input
        JButton button = new JButton("Greet");

        button.addActionListener(e ->
                JOptionPane.showMessageDialog(frame, "Hello, " + textField.getText() + "!"));

        panel.add(label);
        panel.add(textField);
        panel.add(button);

        frame.add(panel);
        frame.setLocationRelativeTo(null);   // center the window on screen
        frame.setVisible(true);              // nothing renders until this is called
    }
}
```

--> **Core component vocabulary**:

| Component | Purpose |
|---|---|
| `JLabel` | Non-editable text or icon display |
| `JTextField` | Single-line editable text input |
| `JTextArea` | Multi-line editable text input (often wrapped in a `JScrollPane`) |
| `JButton` | Clickable push button |
| `JCheckBox` | Independent boolean toggle |
| `JRadioButton` (+ `ButtonGroup`) | Mutually exclusive single choice among a group |
| `JComboBox<T>` | Dropdown single-selection list |
| `JList<T>` | Scrollable list, single or multi-select |
| `JTable` | Tabular/grid data display and editing |
| `JScrollPane` | Wraps another component to add scrollbars when content overflows |
| `JMenuBar` / `JMenu` / `JMenuItem` | Application menu bar and dropdown menus |
| `JDialog` / `JOptionPane` | Modal/non-modal popup windows and pre-built message/input/confirm dialogs |

--> **Deep Dive -- the Event Dispatch Thread (EDT)** -- Swing's entire component tree is single-threaded by design: every read AND write to a Swing component must happen on ONE dedicated thread, the EDT. Constructing/showing your initial UI from `main()` directly (instead of via `SwingUtilities.invokeLater`) technically often "works" in a trivial demo, but is a genuine thread-safety violation that can cause SUBTLE, hard-to-reproduce bugs (partial repaints, deadlocks, corrupted component state) once the app does anything more complex, like updating UI from a background task. Long-running work (network calls, file I/O, heavy computation) must run OFF the EDT (e.g. via `SwingWorker`, covered below) to avoid freezing the entire UI -- Swing has no built-in protection against a slow operation blocking the EDT and making the whole window unresponsive.

# Layout Managers -- Positioning Components

--> Swing does NOT use pixel-perfect absolute positioning by default (though it's technically possible via `setLayout(null)` -- almost always the wrong choice, since it breaks on window resize, font size changes, and different OS DPI settings). Instead, a **layout manager** assigned to a container decides how its children are sized and positioned, adapting automatically to window resizing, look-and-feel changes, and different platforms.

| Layout Manager | Behavior |
|---|---|
| `FlowLayout` | Components flow left-to-right, wrapping to a new row when out of space (default for `JPanel`) |
| `BorderLayout` | Five zones: `NORTH`, `SOUTH`, `EAST`, `WEST`, `CENTER` -- `CENTER` expands to fill remaining space (default for `JFrame`'s content pane) |
| `GridLayout` | Fixed rows x columns grid, all cells EQUAL size |
| `GridBagLayout` | Most flexible/powerful -- fine-grained control over cell spanning, weighting, alignment via `GridBagConstraints`; also the most verbose |
| `BoxLayout` | Single row or column, with fine control over spacing via "struts" and "glue" |

```java
JFrame frame = new JFrame("Layout Demo");
frame.setLayout(new BorderLayout());

frame.add(new JLabel("Header", SwingConstants.CENTER), BorderLayout.NORTH);
frame.add(new JButton("Left"), BorderLayout.WEST);
frame.add(new JButton("Right"), BorderLayout.EAST);
frame.add(new JTextArea(), BorderLayout.CENTER);      // takes all remaining space
frame.add(new JLabel("Footer"), BorderLayout.SOUTH);
```

```java
// GridBagLayout -- verbose but precise; the standard choice for real forms
JPanel form = new JPanel(new GridBagLayout());
GridBagConstraints gbc = new GridBagConstraints();
gbc.insets = new Insets(4, 4, 4, 4);     // padding around every cell
gbc.gridx = 0; gbc.gridy = 0;
form.add(new JLabel("Name:"), gbc);
gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
form.add(new JTextField(20), gbc);       // weightx makes this field grow when the window widens
```

--> **Nesting layouts is the normal pattern** -- real Swing UIs are rarely built with ONE layout manager for the whole window; instead, you nest `JPanel`s, each with its own (often different) layout manager, to compose complex arrangements out of simpler pieces -- e.g. a `BorderLayout` frame whose `CENTER` holds a `JPanel` using `GridBagLayout` for a form.
--> **Gotcha -- forgetting to call `revalidate()`/`repaint()`** -- adding or removing components from an ALREADY-VISIBLE container doesn't automatically re-layout and redraw; call `container.revalidate()` (recompute layout) followed by `container.repaint()` (redraw) after mutating a visible container's children at runtime.

# Event Handling

--> Swing uses the standard Java **event listener** pattern -- a component fires events (button clicks, text changes, list selection changes, key presses), and any number of registered LISTENER objects implementing the matching listener interface get notified.

```java
JButton button = new JButton("Save");

// Lambda form (ActionListener is a functional interface -- one abstract method, actionPerformed)
button.addActionListener(e -> System.out.println("Saved!"));

// Explicit anonymous class form -- equivalent, more verbose, useful when you need
// access to more than the single abstract method or want a named reference to remove later
button.addActionListener(new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("Saved!");
    }
});
```

| Listener Interface | Fires On |
|---|---|
| `ActionListener` | Button click, menu item selection, Enter in a text field |
| `MouseListener` / `MouseMotionListener` | Mouse press/release/click/enter/exit/move/drag |
| `KeyListener` | Raw key press/release/type |
| `ItemListener` | Checkbox/combo-box selection state change |
| `WindowListener` | Window opened/closed/minimized/activated |
| `DocumentListener` | Text content changes in a `JTextField`/`JTextArea`'s underlying `Document` (fires per-edit, not just on Enter) |
| `ListSelectionListener` | Selection change in a `JList`/`JTable` |

--> **Deep Dive -- why `ActionListener` over `KeyListener` for "Enter pressed" in a text field** -- `JTextField` fires `ActionEvent` when Enter is pressed, giving you a clean semantic hook without manually checking `KeyEvent.VK_ENTER` in a raw key listener; prefer the higher-level event when a component already offers one.
--> **Multiple listeners can be attached to the same component** -- `addActionListener` can be called more than once, and all registered listeners fire, in registration order, for every event -- useful for decoupling concerns (e.g. one listener that validates, another that saves), but easy to lose track of if not documented.
--> **`SwingWorker<T, V>`** is the standard way to run a background task without blocking the EDT while still safely updating the UI when done -- it runs `doInBackground()` off the EDT, then automatically marshals `process()` (intermediate progress) and `done()` (final result) calls BACK onto the EDT for you.

```java
class LoadDataWorker extends SwingWorker<List<String>, Void> {
    @Override
    protected List<String> doInBackground() {
        return fetchDataFromNetworkOrDisk();   // runs off the EDT -- safe to block here
    }

    @Override
    protected void done() {
        try {
            List<String> result = get();       // runs back ON the EDT -- safe to touch UI here
            resultList.setListData(result.toArray(new String[0]));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Load failed: " + e.getMessage());
        }
    }
}

new LoadDataWorker().execute();
```

# Is Swing Still Relevant Today?

--> **Short answer: yes, in specific contexts, but it is NOT where new greenfield desktop UI work generally starts.** Oracle has not deprecated Swing and it ships in every JDK release, but active investment/innovation in Java desktop UI has moved to JavaFX (covered in the next file) since around Java 8-11.
--> **Where Swing still shows up in practice**:
  - --> **Legacy enterprise applications** -- large internal tools (banking, insurance, manufacturing/industrial control panels) built 10-20 years ago, still actively maintained, where a full rewrite to JavaFX isn't cost-justified.
  - --> **Internal developer/admin tools** -- quick utilities (a log viewer, a config editor, a small internal dashboard) where "ships with the JDK, zero extra dependencies, good enough look" outweighs modern polish.
  - --> **IDE and tooling codebases** -- IntelliJ IDEA's UI, for instance, is built substantially on Swing (with heavy customization) -- proof that Swing remains viable at real scale when a team invests in it.
  - --> **Existing large Swing codebases** -- incrementally maintaining/extending an existing multi-hundred-thousand-line Swing app is usually far cheaper than a full JavaFX rewrite, even years later.
--> **Where Swing is a poor choice today** -- new greenfield desktop apps wanting modern styling/animations/CSS-based theming, apps targeting touch input, apps wanting hardware-accelerated graphics, or teams with no existing Swing investment -- JavaFX (or a non-Java toolkit entirely) is the better starting point in those cases.

# Common Gotchas

--> **Building/mutating the UI off the EDT** -- the single most common Swing bug class; always wrap startup code in `SwingUtilities.invokeLater`, and marshal any UI update triggered from a background thread back onto the EDT via `SwingUtilities.invokeLater` (or use `SwingWorker`, which does this for you).
--> **`setLayout(null)` + manual pixel positioning** -- tempting for "pixel perfect" control, but breaks immediately on window resize, different OS font metrics, and different screen DPI/scaling -- always prefer a real layout manager, nesting panels as needed.
--> **Forgetting `revalidate()`/`repaint()`** after adding/removing components from an already-visible container at runtime -- the UI silently fails to reflect the change until some unrelated repaint happens to trigger it.
--> **Blocking the EDT with long-running work directly inside a listener** -- e.g. doing a synchronous network call inside `actionPerformed` freezes the ENTIRE UI (no repaints, no input handling) until it returns; always offload to `SwingWorker` or another background-thread mechanism.
--> **Memory leaks from un-removed listeners** -- a listener registered on a long-lived component but referencing a short-lived object (e.g. a closed dialog) can keep that object alive indefinitely; remove listeners (`removeActionListener`, etc.) when a component is genuinely done, especially in long-running desktop apps.

# Best Practices Summary

--> Always start Swing UI construction inside `SwingUtilities.invokeLater` -- never build/show a `JFrame` directly from `main()`.
--> Compose complex layouts by nesting `JPanel`s with different layout managers, rather than fighting one single layout manager (or `null` layout) for the entire window.
--> Offload anything beyond trivial, fast work to `SwingWorker` -- never block the EDT.
--> Prefer higher-level semantic listeners (`ActionListener` on Enter) over low-level ones (`KeyListener`) when a component already exposes them.
--> Call `revalidate()` + `repaint()` after mutating an already-visible container's children.
--> For genuinely new desktop projects with no existing Swing investment, evaluate JavaFX first -- reach for Swing mainly to maintain/extend an existing codebase, or when the zero-extra-dependency, ships-with-the-JDK property specifically matters.
