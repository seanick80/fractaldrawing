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

        addResizeSupport(floatingDialog);
        floatingDialog.setVisible(true);
        docked = false;
    }

    private void addResizeSupport(JDialog dialog) {
        int margin = 6;
        JRootPane root = dialog.getRootPane();
        MouseAdapter resizer = new MouseAdapter() {
            private int edge;
            private Point start;
            private Rectangle startBounds;

            private int detectEdge(MouseEvent e) {
                int x = e.getX(), y = e.getY();
                int w = root.getWidth(), h = root.getHeight();
                boolean r = x >= w - margin, b = y >= h - margin;
                boolean l = x <= margin, t = y <= margin;
                if (r && b) return Cursor.SE_RESIZE_CURSOR;
                if (l && b) return Cursor.SW_RESIZE_CURSOR;
                if (r && t) return Cursor.NE_RESIZE_CURSOR;
                if (l && t) return Cursor.NW_RESIZE_CURSOR;
                if (r) return Cursor.E_RESIZE_CURSOR;
                if (b) return Cursor.S_RESIZE_CURSOR;
                if (l) return Cursor.W_RESIZE_CURSOR;
                if (t) return Cursor.N_RESIZE_CURSOR;
                return 0;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int detected = detectEdge(e);
                root.setCursor(detected == 0
                    ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(detected));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                edge = detectEdge(e);
                start = e.getLocationOnScreen();
                startBounds = dialog.getBounds();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (edge == 0 || start == null) return;
                Point now = e.getLocationOnScreen();
                int dx = now.x - start.x, dy = now.y - start.y;
                Rectangle b = new Rectangle(startBounds);
                int minW = 180, minH = 100;

                boolean east = edge == Cursor.E_RESIZE_CURSOR
                    || edge == Cursor.SE_RESIZE_CURSOR
                    || edge == Cursor.NE_RESIZE_CURSOR;
                boolean south = edge == Cursor.S_RESIZE_CURSOR
                    || edge == Cursor.SE_RESIZE_CURSOR
                    || edge == Cursor.SW_RESIZE_CURSOR;
                boolean west = edge == Cursor.W_RESIZE_CURSOR
                    || edge == Cursor.SW_RESIZE_CURSOR
                    || edge == Cursor.NW_RESIZE_CURSOR;
                boolean north = edge == Cursor.N_RESIZE_CURSOR
                    || edge == Cursor.NE_RESIZE_CURSOR
                    || edge == Cursor.NW_RESIZE_CURSOR;

                if (east) b.width = Math.max(minW, startBounds.width + dx);
                if (south) b.height = Math.max(minH, startBounds.height + dy);
                if (west) {
                    int newW = Math.max(minW, startBounds.width - dx);
                    b.x = startBounds.x + startBounds.width - newW;
                    b.width = newW;
                }
                if (north) {
                    int newH = Math.max(minH, startBounds.height - dy);
                    b.y = startBounds.y + startBounds.height - newH;
                    b.height = newH;
                }
                dialog.setBounds(b);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                edge = 0;
            }
        };
        root.addMouseListener(resizer);
        root.addMouseMotionListener(resizer);
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
