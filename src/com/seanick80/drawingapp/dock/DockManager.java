package com.seanick80.drawingapp.dock;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages dockable panels with edge-based docking.
 * Panels can be docked to any edge of the frame (WEST, EAST, NORTH, SOUTH).
 * Shows a visual preview: edge highlight when empty, insertion line between panels.
 */
public class DockManager {

    public enum DockEdge { WEST, EAST, NORTH, SOUTH }

    private static final int EDGE_ZONE = 60;

    private final JFrame frame;
    private final List<DockablePanel> panels = new ArrayList<>();
    private Runnable layoutCallback;

    // Edge containers
    private final JPanel westContainer = createEdgeContainer(BoxLayout.Y_AXIS);
    private final JPanel eastContainer = createEdgeContainer(BoxLayout.Y_AXIS);
    private final JPanel northContainer = createEdgeContainer(BoxLayout.X_AXIS);
    private final JPanel southContainer = createEdgeContainer(BoxLayout.X_AXIS);

    // Glass pane for dock preview
    private final DockPreviewPane previewPane = new DockPreviewPane();
    private DockEdge activePreviewEdge;
    private int activePreviewIndex = -1;

    public DockManager(JFrame frame) {
        this.frame = frame;
        frame.setGlassPane(previewPane);
    }

    private static JPanel createEdgeContainer(int axis) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, axis));
        return p;
    }

    public JPanel getWestContainer() { return westContainer; }
    public JPanel getEastContainer() { return eastContainer; }
    public JPanel getNorthContainer() { return northContainer; }
    public JPanel getSouthContainer() { return southContainer; }

    public void setLayoutCallback(Runnable callback) {
        this.layoutCallback = callback;
    }

    public void register(DockablePanel panel) {
        panels.add(panel);
    }

    public void toggle(DockablePanel panel) {
        if (panel.isDocked()) {
            undock(panel);
        } else {
            dock(panel);
        }
    }

    public void undock(DockablePanel panel) {
        panel.undock();
        updateContainerVisibility();
        refreshLayout();
    }

    /** Dock panel to its last known edge at the end. */
    public void dock(DockablePanel panel) {
        panel.dock();
        addToEdgeContainer(panel, panel.getDockEdge(), -1);
        updateContainerVisibility();
        refreshLayout();
    }

    /** Dock panel to a specific edge at a specific index. */
    public void dockToEdge(DockablePanel panel, DockEdge edge, int index) {
        panel.setDockEdge(edge);
        panel.dock();
        addToEdgeContainer(panel, edge, index);
        updateContainerVisibility();
        refreshLayout();
    }

    public void show(DockablePanel panel) {
        panel.setHidden(false);
        if (!panel.isDocked()) {
            dock(panel);
        }
        updateContainerVisibility();
        refreshLayout();
    }

    public void hide(DockablePanel panel) {
        if (!panel.isDocked()) {
            panel.dock();
            addToEdgeContainer(panel, panel.getDockEdge(), -1);
        }
        panel.setHidden(true);
        updateContainerVisibility();
        refreshLayout();
    }

    public void dockAll() {
        for (DockablePanel panel : panels) {
            panel.setHidden(false);
            if (!panel.isDocked()) {
                panel.dock();
                addToEdgeContainer(panel, panel.getDockEdge(), -1);
            }
        }
        updateContainerVisibility();
        refreshLayout();
    }

    public JFrame getFrame() {
        return frame;
    }

    public List<DockablePanel> getPanels() {
        return panels;
    }

    // --- Drag preview ---

    /** Result of a drag preview update. */
    public static class DockTarget {
        public final DockEdge edge;
        public final int index;
        public DockTarget(DockEdge edge, int index) {
            this.edge = edge;
            this.index = index;
        }
    }

    /** Called during drag to show/update the dock preview. Returns target or null. */
    public DockTarget updateDragPreview(Point screenPos) {
        if (!frame.isShowing()) return null;

        Container contentPane = frame.getContentPane();
        Point cpLoc = contentPane.getLocationOnScreen();
        int fx = screenPos.x - cpLoc.x;
        int fy = screenPos.y - cpLoc.y;
        int fw = contentPane.getWidth();
        int fh = contentPane.getHeight();

        DockEdge edge = null;
        if (fx >= 0 && fx < EDGE_ZONE && fy >= 0 && fy < fh) {
            edge = DockEdge.WEST;
        } else if (fx > fw - EDGE_ZONE && fx <= fw && fy >= 0 && fy < fh) {
            edge = DockEdge.EAST;
        } else if (fy >= 0 && fy < EDGE_ZONE && fx >= 0 && fx < fw) {
            edge = DockEdge.NORTH;
        } else if (fy > fh - EDGE_ZONE && fy <= fh && fx >= 0 && fx < fw) {
            edge = DockEdge.SOUTH;
        }

        if (edge == null) {
            if (activePreviewEdge != null) {
                activePreviewEdge = null;
                activePreviewIndex = -1;
                previewPane.clearHighlight();
                previewPane.setVisible(false);
            }
            return null;
        }

        // Determine insertion index within the container
        JPanel container = getContainer(edge);
        boolean vertical = (edge == DockEdge.WEST || edge == DockEdge.EAST);
        int insertIndex = computeInsertIndex(container, screenPos, vertical);

        if (edge != activePreviewEdge || insertIndex != activePreviewIndex) {
            activePreviewEdge = edge;
            activePreviewIndex = insertIndex;
            updatePreviewGraphic(edge, container, insertIndex, vertical, fw, fh);
            previewPane.setVisible(true);
        }

        return new DockTarget(edge, insertIndex);
    }

    /** Called when drag ends to clear the preview. */
    public void clearDragPreview() {
        activePreviewEdge = null;
        activePreviewIndex = -1;
        previewPane.clearHighlight();
        previewPane.setVisible(false);
    }

    // --- Internal ---

    private JPanel getContainer(DockEdge edge) {
        return switch (edge) {
            case WEST -> westContainer;
            case EAST -> eastContainer;
            case NORTH -> northContainer;
            case SOUTH -> southContainer;
        };
    }

    private int computeInsertIndex(JPanel container, Point screenPos, boolean vertical) {
        int count = container.getComponentCount();
        if (count == 0 || !container.isShowing()) return 0;

        Point containerLoc = container.getLocationOnScreen();

        for (int i = 0; i < count; i++) {
            Component child = container.getComponent(i);
            if (!child.isVisible()) continue;

            if (vertical) {
                int childMid = containerLoc.y + child.getY() + child.getHeight() / 2;
                if (screenPos.y < childMid) return i;
            } else {
                int childMid = containerLoc.x + child.getX() + child.getWidth() / 2;
                if (screenPos.x < childMid) return i;
            }
        }
        return count;
    }

    private void updatePreviewGraphic(DockEdge edge, JPanel container,
                                       int insertIndex, boolean vertical,
                                       int fw, int fh) {
        // If container is empty or not showing, show edge highlight
        if (container.getComponentCount() == 0 || !container.isShowing()) {
            int thickness = 6;
            Rectangle rect = switch (edge) {
                case WEST -> new Rectangle(0, 0, thickness, fh);
                case EAST -> new Rectangle(fw - thickness, 0, thickness, fh);
                case NORTH -> new Rectangle(0, 0, fw, thickness);
                case SOUTH -> new Rectangle(0, fh - thickness, fw, thickness);
            };
            previewPane.setHighlight(rect);
            return;
        }

        // Show insertion line between panels
        Container contentPane = frame.getContentPane();
        Point cpScreen = contentPane.getLocationOnScreen();
        Point containerScreen = container.getLocationOnScreen();

        // Relative to content pane
        int cx = containerScreen.x - cpScreen.x;
        int cy = containerScreen.y - cpScreen.y;

        int lineThickness = 4;
        Rectangle lineRect;

        if (vertical) {
            int lineY;
            if (insertIndex >= container.getComponentCount()) {
                Component last = container.getComponent(container.getComponentCount() - 1);
                lineY = cy + last.getY() + last.getHeight();
            } else {
                Component at = container.getComponent(insertIndex);
                lineY = cy + at.getY();
            }
            lineRect = new Rectangle(cx, lineY - lineThickness / 2,
                container.getWidth(), lineThickness);
        } else {
            int lineX;
            if (insertIndex >= container.getComponentCount()) {
                Component last = container.getComponent(container.getComponentCount() - 1);
                lineX = cx + last.getX() + last.getWidth();
            } else {
                Component at = container.getComponent(insertIndex);
                lineX = cx + at.getX();
            }
            lineRect = new Rectangle(lineX - lineThickness / 2, cy,
                lineThickness, container.getHeight());
        }

        previewPane.setHighlight(lineRect);
    }

    private void addToEdgeContainer(DockablePanel panel, DockEdge edge, int index) {
        // Remove from all containers first
        westContainer.remove(panel);
        eastContainer.remove(panel);
        northContainer.remove(panel);
        southContainer.remove(panel);

        JPanel container = getContainer(edge);
        if (index < 0 || index >= container.getComponentCount()) {
            container.add(panel);
        } else {
            container.add(panel, index);
        }
    }

    private void updateContainerVisibility() {
        westContainer.setVisible(hasVisibleDockedPanel(westContainer));
        eastContainer.setVisible(hasVisibleDockedPanel(eastContainer));
        northContainer.setVisible(hasVisibleDockedPanel(northContainer));
        southContainer.setVisible(hasVisibleDockedPanel(southContainer));
    }

    private boolean hasVisibleDockedPanel(JPanel container) {
        for (Component c : container.getComponents()) {
            if (c instanceof DockablePanel dp && dp.isDocked() && !dp.isHidden()) {
                return true;
            }
        }
        return false;
    }

    private void refreshLayout() {
        if (layoutCallback != null) {
            layoutCallback.run();
        }
        frame.revalidate();
        frame.repaint();
    }

    // --- Glass pane for dock preview ---

    private static class DockPreviewPane extends JPanel {
        private Rectangle highlightRect;

        DockPreviewPane() {
            setOpaque(false);
        }

        void setHighlight(Rectangle rect) {
            highlightRect = rect;
            repaint();
        }

        void clearHighlight() {
            highlightRect = null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (highlightRect != null) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(60, 120, 220, 160));
                g2.fill(highlightRect);
                g2.setColor(new Color(40, 90, 200, 220));
                g2.setStroke(new BasicStroke(2));
                g2.draw(highlightRect);
                g2.dispose();
            }
        }
    }
}
