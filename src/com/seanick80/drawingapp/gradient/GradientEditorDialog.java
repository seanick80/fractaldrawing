package com.seanick80.drawingapp.gradient;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

/**
 * Modal dialog for editing a ColorGradient.
 * Returns the edited gradient on OK, or null on Cancel.
 */
public class GradientEditorDialog extends JDialog {

    private final GradientEditorPanel editorPanel;
    private boolean accepted;
    private static File lastDirectory;

    public GradientEditorDialog(Window owner, ColorGradient gradient) {
        super(owner, "Gradient Editor", ModalityType.APPLICATION_MODAL);
        ColorGradient workingCopy = new ColorGradient(gradient);
        editorPanel = new GradientEditorPanel(workingCopy);

        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Help text
        JLabel helpLabel = new JLabel(
            "<html><b>Shift+click</b> to add stop &nbsp; " +
            "<b>Right-click</b> to delete &nbsp; " +
            "<b>Double-click</b> for color chooser &nbsp; " +
            "<b>Drag</b> to move</html>"
        );
        helpLabel.setFont(helpLabel.getFont().deriveFont(10f));
        helpLabel.setForeground(Color.GRAY);

        // File buttons
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton loadButton = new JButton("Load...");
        JButton saveButton = new JButton("Save...");
        JButton resetButton = new JButton("Reset");
        loadButton.addActionListener(e -> loadGradient());
        saveButton.addActionListener(e -> saveGradient());
        resetButton.addActionListener(e -> {
            ColorGradient current = editorPanel.getGradient();
            current.getStops().clear();
            current.addStop(0f, Color.BLACK);
            current.addStop(1f, Color.WHITE);
            editorPanel.setSelectedStop(null);
            editorPanel.repaint();
        });
        filePanel.add(loadButton);
        filePanel.add(saveButton);
        filePanel.add(resetButton);

        // OK/Cancel buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> { accepted = true; dispose(); });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Bottom panel with help + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(filePanel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        // Help text above the bottom buttons
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(helpLabel, BorderLayout.NORTH);
        southPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(editorPanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        setSize(600, 420);
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(okButton);
    }

    private JFileChooser createChooser() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileFilter(new FileNameExtensionFilter("Gradient files (*.grd)", "grd"));
        return chooser;
    }

    private void loadGradient() {
        JFileChooser chooser = createChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            try {
                ColorGradient loaded = ColorGradient.load(chooser.getSelectedFile());
                // Replace the editor panel's gradient stops
                ColorGradient current = editorPanel.getGradient();
                current.getStops().clear();
                for (ColorGradient.Stop s : loaded.getStops()) {
                    current.addStop(s.getPosition(), s.getColor());
                }
                editorPanel.setSelectedStop(null);
                editorPanel.repaint();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to load gradient: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveGradient() {
        JFileChooser chooser = createChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            try {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".grd")) {
                    file = new File(file.getPath() + ".grd");
                }
                if (file.exists()) {
                    int result = JOptionPane.showConfirmDialog(this,
                        file.getName() + " already exists.\nDo you want to replace it?",
                        "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (result != JOptionPane.YES_OPTION) return;
                }
                editorPanel.getGradient().save(file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save gradient: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Show the dialog and return the edited gradient, or null if cancelled.
     */
    public static ColorGradient showDialog(Window owner, ColorGradient gradient) {
        GradientEditorDialog dlg = new GradientEditorDialog(owner, gradient);
        dlg.setVisible(true);
        return dlg.accepted ? dlg.editorPanel.getGradient() : null;
    }
}
