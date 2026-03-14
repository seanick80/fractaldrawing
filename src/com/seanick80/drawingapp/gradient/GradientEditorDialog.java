package com.seanick80.drawingapp.gradient;

import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog for editing a ColorGradient.
 * Returns the edited gradient on OK, or null on Cancel.
 */
public class GradientEditorDialog extends JDialog {

    private final GradientEditorPanel editorPanel;
    private boolean accepted;

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

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> { accepted = true; dispose(); });
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // Bottom panel with help + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(helpLabel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(editorPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(600, 420);
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(okButton);
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
