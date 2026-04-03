package com.seanick80.drawingapp.dock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * A wrapper that makes any JPanel floatable.
 * Shows a title bar with an undock button. When undocked, the content
 * moves into a floating JDialog. Closing the dialog re-docks the panel.
 */
public class DockablePanel extends JPanel {

    private final String title;
    private final JPanel contentPanel;
    private final JButton toggleButton;
    private final DockManager dockManager;

    private JDialog floatingDialog;
    private boolean docked = true;
    private Dimension floatingSize;
    private Point floatingLocation;

    public DockablePanel(String title, JPanel contentPanel, DockManager manager) {
        this.title = title;
        this.contentPanel = contentPanel;
        this.dockManager = manager;

        setLayout(new BorderLayout());

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(220, 220, 225));
        titleBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));

        toggleButton = new JButton("\u2197"); // north-east arrow = pop out
        toggleButton.setFont(toggleButton.getFont().deriveFont(10f));
        toggleButton.setMargin(new Insets(0, 3, 0, 3));
        toggleButton.setFocusable(false);
        toggleButton.setToolTipText("Float panel");
        toggleButton.addActionListener(e -> dockManager.toggle(this));

        titleBar.add(titleLabel, BorderLayout.CENTER);
        titleBar.add(toggleButton, BorderLayout.EAST);

        add(titleBar, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        manager.register(this);
    }

    public void undock() {
        remove(contentPanel);
        setVisible(false);

        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null) owner = new JFrame();

        floatingDialog = new JDialog((Frame) (owner instanceof Frame ? owner : null), title, false);
        floatingDialog.add(contentPanel);
        floatingDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        floatingDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dockManager.dock(DockablePanel.this);
            }
        });

        if (floatingSize != null) {
            floatingDialog.setSize(floatingSize);
        } else {
            floatingDialog.pack();
            Dimension d = floatingDialog.getSize();
            floatingDialog.setSize(Math.max(d.width, 180), Math.max(d.height, 200));
        }

        if (floatingLocation != null) {
            floatingDialog.setLocation(floatingLocation);
        } else if (owner != null && owner.isShowing()) {
            Point ownerLoc = owner.getLocationOnScreen();
            floatingDialog.setLocation(ownerLoc.x + owner.getWidth() + 5, ownerLoc.y);
        }

        floatingDialog.setVisible(true);
        docked = false;
        toggleButton.setText("\u2199"); // south-west arrow = dock
        toggleButton.setToolTipText("Dock panel");
    }

    public void dock() {
        if (floatingDialog != null) {
            floatingSize = floatingDialog.getSize();
            floatingLocation = floatingDialog.getLocation();
            floatingDialog.dispose();
            floatingDialog = null;
        }

        add(contentPanel, BorderLayout.CENTER);
        setVisible(true);
        docked = true;
        toggleButton.setText("\u2197");
        toggleButton.setToolTipText("Float panel");
    }

    public boolean isDocked() {
        return docked;
    }

    public String getTitle() {
        return title;
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }
}
