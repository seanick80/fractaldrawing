package com.seanick80.drawingapp.dock;

import com.seanick80.drawingapp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import javax.swing.*;
import java.awt.*;

class DockSystemTest {

    private JFrame frame;
    private DockManager manager;
    private DockablePanel dp1, dp2, dp3;
    private JPanel content1, content2, content3;

    @BeforeEach
    void setUp() {
        frame = new JFrame();
        manager = new DockManager(frame);
        content1 = new JPanel();
        content2 = new JPanel();
        content3 = new JPanel();
        dp1 = new DockablePanel("Panel A", content1, manager);
        dp2 = new DockablePanel("Panel B", content2, manager);
        dp3 = new DockablePanel("Panel C", content3, manager);
    }

    @AfterEach
    void tearDown() {
        frame.dispose();
    }

    @Test @MediumTest
    void initialState() {
        assertTrue(dp1.isDocked());
        assertEquals("Panel A", dp1.getTitle());
        assertSame(content1, dp1.getContentPanel());
        assertEquals(3, manager.getPanels().size());
        assertSame(dp1, manager.getPanels().get(0));
        assertSame(dp2, manager.getPanels().get(1));
    }

    @Test @MediumTest
    void panelContainsContent() {
        boolean hasContent = false;
        for (Component c : dp1.getComponents()) {
            if (c == content1) hasContent = true;
        }
        assertTrue(hasContent);
    }

    @Test @MediumTest
    void dockOnDockedIsNoOp() {
        dp1.dock();
        assertTrue(dp1.isDocked());
    }

    @Test @MediumTest
    void layoutCallbackFires() {
        int[] count = {0};
        manager.setLayoutCallback(() -> count[0]++);

        manager.dock(dp1);
        assertEquals(1, count[0]);

        count[0] = 0;
        manager.dockAll();
        assertEquals(1, count[0]);
    }

    @Test @MediumTest
    void dockEdgeTracking() {
        assertEquals(DockManager.DockEdge.WEST, dp1.getDockEdge());

        dp1.setDockEdge(DockManager.DockEdge.EAST);
        assertEquals(DockManager.DockEdge.EAST, dp1.getDockEdge());

        dp1.setDockEdge(DockManager.DockEdge.NORTH);
        assertEquals(DockManager.DockEdge.NORTH, dp1.getDockEdge());

        dp1.setDockEdge(DockManager.DockEdge.SOUTH);
        assertEquals(DockManager.DockEdge.SOUTH, dp1.getDockEdge());

        dp1.setDockEdge(DockManager.DockEdge.WEST);
        assertEquals(DockManager.DockEdge.WEST, dp1.getDockEdge());
    }

    @Test @MediumTest
    void dockTarget() {
        DockManager.DockTarget target = new DockManager.DockTarget(DockManager.DockEdge.EAST, 2);
        assertEquals(DockManager.DockEdge.EAST, target.edge);
        assertEquals(2, target.index);

        DockManager.DockTarget targetWest = new DockManager.DockTarget(DockManager.DockEdge.WEST, 0);
        assertEquals(DockManager.DockEdge.WEST, targetWest.edge);
        assertEquals(0, targetWest.index);
    }

    @Test @MediumTest
    void hideShow() {
        assertFalse(dp2.isHidden());

        dp2.setHidden(true);
        assertTrue(dp2.isHidden());
        assertFalse(dp2.isVisible());

        dp2.setHidden(false);
        assertFalse(dp2.isHidden());
        assertTrue(dp2.isVisible());

        int[] count = {0};
        manager.setLayoutCallback(() -> count[0]++);

        manager.hide(dp3);
        assertTrue(dp3.isHidden());
        assertTrue(count[0] >= 1);

        count[0] = 0;
        manager.show(dp3);
        assertFalse(dp3.isHidden());
        assertTrue(count[0] >= 1);

        dp2.setHidden(true);
        dp3.setHidden(true);
        manager.dockAll();
        assertFalse(dp2.isHidden());
        assertFalse(dp3.isHidden());
    }

    @Test @MediumTest
    void edgeContainers() {
        assertNotNull(manager.getWestContainer());
        assertNotNull(manager.getEastContainer());
        assertNotNull(manager.getNorthContainer());
        assertNotNull(manager.getSouthContainer());

        assertNotSame(manager.getWestContainer(), manager.getEastContainer());
        assertNotSame(manager.getNorthContainer(), manager.getSouthContainer());
        assertNotSame(manager.getWestContainer(), manager.getNorthContainer());
    }

    @Test @MediumTest
    void dockToEdge() {
        int[] count = {0};
        manager.setLayoutCallback(() -> count[0]++);

        manager.dockToEdge(dp1, DockManager.DockEdge.EAST, 0);
        assertEquals(DockManager.DockEdge.EAST, dp1.getDockEdge());
        assertTrue(dp1.isDocked());
        assertTrue(count[0] >= 1);

        boolean inEast = false;
        for (Component c : manager.getEastContainer().getComponents()) {
            if (c == dp1) inEast = true;
        }
        assertTrue(inEast);

        boolean inWest = false;
        for (Component c : manager.getWestContainer().getComponents()) {
            if (c == dp1) inWest = true;
        }
        assertFalse(inWest);

        manager.dockToEdge(dp2, DockManager.DockEdge.EAST, 0);
        assertSame(dp2, manager.getEastContainer().getComponent(0));
        assertSame(dp1, manager.getEastContainer().getComponent(1));

        manager.dockToEdge(dp3, DockManager.DockEdge.WEST, 0);
        assertSame(dp3, manager.getWestContainer().getComponent(0));

        manager.dockAll();
        assertTrue(dp1.isDocked());
        assertTrue(dp2.isDocked());
        assertTrue(dp3.isDocked());
    }
}
