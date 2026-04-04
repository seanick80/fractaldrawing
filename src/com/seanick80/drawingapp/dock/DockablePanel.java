package com.seanick80.drawingapp.dock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * A wrapper that makes any JPanel floatable.
 * Drag the title bar to undock into a floating dialog.
 * Drag near a frame edge to see a dock preview; release to dock there.
 * Close button on title bar re-docks when floating.
 *
 * The entire DockablePanel (title bar + content) moves into an undecorated
 * JDialog when floating, so the title bar mouse events work in both states.
 */
public class DockablePanel extends JPanel {

    private final String title;
    private final JPanel contentPanel;
    private final DockManager dockManager;
    private final JButton closeButton;

    private JDialog floatingDialog;
    private boolean docked = true;
    private boolean hidden = false;
    private DockManager.DockEdge dockEdge = DockManager.DockEdge.WEST;
    private Dimension floatingSize;
    private Point floatingLocation;

    private static final int DRAG_THRESHOLD = 8;

    // Drag state
    private Point dragStart;
    private Point dialogOffset;
    private boolean dragActive;

    public DockablePanel(String title, JPanel contentPanel, DockManager manager) {
        this.title = title;
        this.contentPanel = contentPanel;
        this.dockManager = manager;

        setLayout(new BorderLayout());

        // Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(new Color(220, 220, 225));
        titleBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 2));
        titleBar.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleBar.add(titleLabel, BorderLayout.CENTER);

        // Close button (only visible when floating, re-docks the panel)
        closeButton = new JButton("\u2715");
        closeButton.setFont(closeButton.getFont().deriveFont(10f));
        closeButton.setMargin(new Insets(0, 3, 0, 3));
        closeButton.setFocusable(false);
        closeButton.setVisible(false);
        closeButton.addActionListener(e -> dockManager.dock(this));
        titleBar.add(closeButton, BorderLayout.EAST);

        // Drag handling on title bar
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getLocationOnScreen();
                dialogOffset = null;
                dragActive = true;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragActive) return;
                Point now = e.getLocationOnScreen();

                if (docked) {
                    // Undock once drag exceeds threshold
                    if (dragStart != null &&
                        now.distance(dragStart) > DRAG_THRESHOLD) {
                        dockManager.undock(DockablePanel.this);
                        // Position dialog centered on cursor
                        if (floatingDialog != null) {
                            dialogOffset = new Point(
                                floatingDialog.getWidth() / 2, 12);
                            floatingDialog.setLocation(
                                now.x - dialogOffset.x,
                                now.y - dialogOffset.y);
                        }
                    }
                } else if (floatingDialog != null) {
                    // Move the floating dialog
                    if (dialogOffset == null) {
                        dialogOffset = new Point(
                            now.x - floatingDialog.getX(),
                            now.y - floatingDialog.getY());
                    }
                    floatingDialog.setLocation(
                        now.x - dialogOffset.x,
                        now.y - dialogOffset.y);
                }
                // Show dock preview near frame edges
                dockManager.updateDragPreview(now);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragActive) return;
                dragActive = false;

                if (!docked && floatingDialog != null) {
                    DockManager.DockTarget target =
                        dockManager.updateDragPreview(e.getLocationOnScreen());
                    if (target != null) {
                        dockManager.dockToEdge(DockablePanel.this, target.edge, target.index);
                    }
                }
                dockManager.clearDragPreview();
                dragStart = null;
                dialogOffset = null;
            }
        };
        titleBar.addMouseListener(dragHandler);
        titleBar.addMouseMotionListener(dragHandler);

        add(titleBar, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        manager.register(this);
    }

    public void undock() {
        // Remove this entire panel from its dock container
        Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.revalidate();
            parent.repaint();
        }

        Frame owner = dockManager.getFrame();

        // Undecorated dialog — our title bar provides drag and close
        floatingDialog = new JDialog(owner, (String) null, false);
        floatingDialog.setUndecorated(true);
        floatingDialog.getRootPane().setBorder(
            BorderFactory.createLineBorder(new Color(160, 160, 165), 1));
        floatingDialog.add(this);
        closeButton.setVisible(true);

        if (floatingSize != null) {
            floatingDialog.setSize(floatingSize);
        } else {
            floatingDialog.pack();
            Dimension d = floatingDialog.getSize();
            floatingDialog.setSize(Math.max(d.width, 180), Math.max(d.height, 200));
        }

        if (floatingLocation != null) {
            floatingDialog.setLocation(floatingLocation);
        } else if (owner.isShowing()) {
            Point ownerLoc = owner.getLocationOnScreen();
            floatingDialog.setLocation(ownerLoc.x + owner.getWidth() + 5, ownerLoc.y);
        }

        floatingDialog.setVisible(true);
        docked = false;
    }

    public void dock() {
        if (floatingDialog != null) {
            floatingSize = floatingDialog.getSize();
            floatingLocation = floatingDialog.getLocation();
            floatingDialog.remove(this);
            floatingDialog.dispose();
            floatingDialog = null;
        }

        closeButton.setVisible(false);
        setVisible(!hidden);
        docked = true;
    }

    /** Repack the floating dialog if content changed (e.g. tool switch). */
    public void repackIfFloating() {
        if (!docked && floatingDialog != null) {
            floatingDialog.pack();
            Dimension d = floatingDialog.getSize();
            floatingDialog.setSize(Math.max(d.width, 180), Math.max(d.height, 200));
        }
    }

    public boolean isDocked() {
        return docked;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
        if (docked) setVisible(!hidden);
    }

    public DockManager.DockEdge getDockEdge() {
        return dockEdge;
    }

    public void setDockEdge(DockManager.DockEdge edge) {
        this.dockEdge = edge;
    }

    public String getTitle() {
        return title;
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }
}
