/*
 * SwingFundamentalsDemo.java
 *
 * Demonstrates:
 *     1. The EDT rule -- building the UI inside SwingUtilities.invokeLater, never
 *        directly from main()
 *     2. JFrame as the top-level window, JPanel as a nested, independently-laid-out
 *        container
 *     3. Core components: JLabel, JTextField, JButton, JCheckBox, JComboBox, JTextArea
 *     4. Layout managers: BorderLayout (frame-level) nested with FlowLayout and
 *        GridBagLayout (panel-level), matching the Theory file's "nest panels, each
 *        with its own layout manager" guidance
 *     5. Event handling via ActionListener (lambda form) and an ItemListener,
 *        including revalidate()/repaint() after mutating an already-visible container
 *     6. A SwingWorker running simulated background work off the EDT, then safely
 *        updating the UI from done()
 *
 * Covers Theory chapter:
 *     10) Java/Desktop GUI and Internationalization/Theory/01 Swing Fundamentals for Desktop GUIs.md
 *
 * This file is GENUINELY RUNNABLE, standalone JDK code -- javax.swing/java.awt ship
 * with every JDK, no external dependencies needed.
 *
 * IMPORTANT -- this program opens a real GUI window (JFrame.setVisible(true)) and
 * therefore needs a display/desktop session available to actually SEE it run (it
 * will fail with a HeadlessException on a true headless server with no X server /
 * virtual display configured). The code itself is valid, compilable, standalone
 * Swing code regardless -- `javac` does not need a display to succeed.
 *
 * Compile: javac 03_swing_fundamentals_demo.java
 * Run:     java SwingFundamentalsDemo
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SwingFundamentalsDemo {

    public static void main(String[] args) {
        // Rule #1 from the Theory file's EDT deep-dive: never build/show a JFrame
        // directly from main() -- schedule UI construction to run ON the Event
        // Dispatch Thread via SwingUtilities.invokeLater instead.
        SwingUtilities.invokeLater(SwingFundamentalsDemo::createAndShowGui);
    }

    private static void createAndShowGui() {
        // ---------------------------------------------------------------
        // 1) JFrame -- the top-level window. BorderLayout is JFrame's
        //    content pane default, used explicitly here for clarity.
        // ---------------------------------------------------------------
        JFrame frame = new JFrame("Swing Fundamentals Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setSize(520, 420);

        // ---------------------------------------------------------------
        // 2) NORTH -- a simple FlowLayout panel (JPanel's own default
        //    layout), showing a JLabel + JTextField + JButton greeting form.
        // ---------------------------------------------------------------
        JPanel greetingPanel = new JPanel();            // default layout: FlowLayout
        JLabel nameLabel = new JLabel("Enter your name:");
        JTextField nameField = new JTextField(15);
        JButton greetButton = new JButton("Greet");

        JTextArea outputArea = new JTextArea(8, 40);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);

        // Event handling -- ActionListener via lambda (ActionListener is a
        // functional interface: one abstract method, actionPerformed).
        ActionListener greetAction = e -> {
            String name = nameField.getText().trim();
            String message = name.isEmpty()
                    ? "Please enter a name first."
                    : "Hello, " + name + "! Welcome to Swing.";
            outputArea.append(message + System.lineSeparator());
        };
        greetButton.addActionListener(greetAction);
        // JTextField itself fires an ActionEvent on Enter -- reuse the SAME
        // listener so pressing Enter in the field behaves like clicking Greet,
        // per the Theory file's "prefer higher-level listeners" guidance
        // (ActionListener-on-Enter instead of a raw KeyListener).
        nameField.addActionListener(greetAction);

        greetingPanel.add(nameLabel);
        greetingPanel.add(nameField);
        greetingPanel.add(greetButton);

        // ---------------------------------------------------------------
        // 3) CENTER -- a GridBagLayout form panel, the Theory file's
        //    "standard choice for real forms" -- more verbose than
        //    FlowLayout, but precise control over cell placement/growth.
        // ---------------------------------------------------------------
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Preferences (GridBagLayout)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Favorite language:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JComboBox<String> languageCombo = new JComboBox<>(new String[] { "Java", "Kotlin", "Python", "Rust" });
        formPanel.add(languageCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("Notify on selection:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        JCheckBox notifyCheckBox = new JCheckBox("Log combo-box changes below");
        formPanel.add(notifyCheckBox, gbc);

        // ItemListener -- fires on JComboBox / JCheckBox selection state changes,
        // a different listener interface than ActionListener (see the Theory
        // file's event-listener table).
        ItemListener comboListener = e -> {
            if (notifyCheckBox.isSelected() && e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                outputArea.append("Language changed to: " + languageCombo.getSelectedItem() + System.lineSeparator());
            }
        };
        languageCombo.addItemListener(comboListener);

        // ---------------------------------------------------------------
        // 4) A button that mutates the visible container at runtime --
        //    demonstrates the Theory file's revalidate()/repaint() gotcha:
        //    adding a component to an ALREADY-VISIBLE container doesn't
        //    automatically re-layout/redraw without these calls.
        // ---------------------------------------------------------------
        JButton addFieldButton = new JButton("Add extra field");
        final int[] extraFieldCount = { 0 };   // effectively-final mutable counter for the lambda below
        addFieldButton.addActionListener(e -> {
            extraFieldCount[0]++;
            gbc.gridx = 0; gbc.gridy = 1 + extraFieldCount[0];
            formPanel.add(new JLabel("Extra field " + extraFieldCount[0] + ":"), gbc);
            gbc.gridx = 1;
            formPanel.add(new JTextField(10), gbc);

            // Required after mutating an already-visible container's children --
            // without these, the new row would not appear until some unrelated
            // repaint happened to trigger it (per the Theory file's gotcha).
            formPanel.revalidate();
            formPanel.repaint();
            outputArea.append("Added extra field #" + extraFieldCount[0] + " (revalidate()+repaint() applied)"
                    + System.lineSeparator());
        });

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.add(formPanel, BorderLayout.CENTER);
        formWrapper.add(addFieldButton, BorderLayout.SOUTH);

        // ---------------------------------------------------------------
        // 5) SOUTH -- a button that kicks off a SwingWorker, demonstrating
        //    background work that never blocks the EDT.
        // ---------------------------------------------------------------
        JButton loadButton = new JButton("Run background task (SwingWorker)");
        JLabel statusLabel = new JLabel("Status: idle");
        loadButton.addActionListener(e -> {
            loadButton.setEnabled(false);
            statusLabel.setText("Status: working...");
            new SimulatedLoadWorker(outputArea, statusLabel, loadButton).execute();
        });

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        southPanel.add(loadButton);
        southPanel.add(statusLabel);

        // ---------------------------------------------------------------
        // Assemble -- BorderLayout zones on the frame itself, per the
        // Theory file's "nesting layouts is the normal pattern" guidance.
        // ---------------------------------------------------------------
        frame.add(greetingPanel, BorderLayout.NORTH);
        frame.add(formWrapper, BorderLayout.WEST);
        frame.add(outputScrollPane, BorderLayout.CENTER);
        frame.add(southPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);   // center on screen
        frame.setVisible(true);               // nothing renders until this is called

        outputArea.append("Swing Fundamentals Demo ready. Try typing a name and pressing Enter or Greet."
                + System.lineSeparator());
    }

    /**
     * SwingWorker<ResultType, IntermediateType> -- runs doInBackground() OFF the
     * EDT (safe to "block" here, simulated with Thread.sleep), then automatically
     * marshals done() back ONTO the EDT so touching Swing components is safe.
     * Mirrors the Theory file's LoadDataWorker example.
     */
    private static class SimulatedLoadWorker extends SwingWorker<List<String>, Void> {
        private final JTextArea outputArea;
        private final JLabel statusLabel;
        private final JButton loadButton;

        SimulatedLoadWorker(JTextArea outputArea, JLabel statusLabel, JButton loadButton) {
            this.outputArea = outputArea;
            this.statusLabel = statusLabel;
            this.loadButton = loadButton;
        }

        @Override
        protected List<String> doInBackground() throws Exception {
            // Runs on a background thread, NOT the EDT -- safe to simulate a
            // slow network/disk operation here without freezing the UI.
            Thread.sleep(800);
            return List.of("row-1", "row-2", "row-3");
        }

        @Override
        protected void done() {
            // Runs back ON the EDT -- safe to touch Swing components here.
            try {
                List<String> result = get();
                outputArea.append("Background task finished, loaded " + result.size() + " row(s): "
                        + result + System.lineSeparator());
                statusLabel.setText("Status: idle");
            } catch (InterruptedException | ExecutionException ex) {
                statusLabel.setText("Status: failed");
                outputArea.append("Background task failed: " + ex.getMessage() + System.lineSeparator());
            } finally {
                loadButton.setEnabled(true);
            }
        }
    }
}
