package com.seanick80.drawingapp.dock;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages dockable panels: tracks state, coordinates layout updates.
 * After each dock/undock, revalidates the frame and fires a change callback.
 */
public class DockManager {

    private final JFrame frame;
    private final List<DockablePanel> panels = new ArrayList<>();
    private Runnable layoutCallback;

    public DockManager(JFrame frame) {
        this.frame = frame;
    }

    /** Set a callback that runs after every dock/undock to update side panel visibility. */
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
        refreshLayout();
    }

    public void dock(DockablePanel panel) {
        panel.dock();
        refreshLayout();
    }

    public void dockAll() {
        for (DockablePanel panel : panels) {
            if (!panel.isDocked()) {
                panel.dock();
            }
        }
        refreshLayout();
    }

    public List<DockablePanel> getPanels() {
        return panels;
    }

    private void refreshLayout() {
        if (layoutCallback != null) {
            layoutCallback.run();
        }
        frame.revalidate();
        frame.repaint();
    }
}
