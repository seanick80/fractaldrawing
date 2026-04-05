package com.seanick80.drawingapp;

import com.seanick80.drawingapp.layers.LayerManager;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class UndoManagerTest {

    @Test @SmallTest
    void initialState_cannotUndoOrRedo() {
        UndoManager um = new UndoManager(100);
        assertFalse(um.canUndo());
        assertFalse(um.canRedo());
    }

    @Test @SmallTest
    void undoRestoresState() {
        LayerManager lm = new LayerManager(50, 50);
        UndoManager um = new UndoManager(100);

        // Draw red onto the active layer
        Graphics2D g1 = lm.getActiveLayer().getImage().createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 50, 50);
        g1.dispose();

        // Save state with red content
        um.saveState(lm);

        // Draw blue on top
        Graphics2D g2 = lm.getActiveLayer().getImage().createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 50, 50);
        g2.dispose();

        // Undo should restore red
        um.undo(lm);

        int pixel = lm.getActiveLayer().getImage().getRGB(0, 0);
        assertTrue((pixel & 0x00FF0000) != 0, "Red channel should be non-zero after undo");
        assertTrue(um.canRedo());
    }

    @Test @SmallTest
    void redoRestoresState() {
        LayerManager lm = new LayerManager(50, 50);
        UndoManager um = new UndoManager(100);

        // Draw red and save
        Graphics2D g1 = lm.getActiveLayer().getImage().createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 50, 50);
        g1.dispose();
        um.saveState(lm);

        // Draw blue
        Graphics2D g2 = lm.getActiveLayer().getImage().createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 50, 50);
        g2.dispose();

        // Undo then redo
        um.undo(lm);
        um.redo(lm);

        int pixel = lm.getActiveLayer().getImage().getRGB(0, 0);
        assertTrue((pixel & 0x000000FF) != 0, "Blue channel should be non-zero after redo");
    }

    @Test @SmallTest
    void newState_clearsRedo() {
        LayerManager lm = new LayerManager(50, 50);
        UndoManager um = new UndoManager(100);

        um.saveState(lm);
        um.saveState(lm);
        um.undo(lm);
        assertTrue(um.canRedo());

        // Saving a new state should clear the redo stack
        um.saveState(lm);
        assertFalse(um.canRedo());
    }

    @Test @SmallTest
    void compaction_reducesStack() {
        LayerManager lm = new LayerManager(50, 50);
        UndoManager um = new UndoManager(200);

        // Save 85 states — crosses the compaction threshold of 80
        for (int i = 0; i < 85; i++) {
            um.saveState(lm);
        }

        // Count the number of undos available
        int count = 0;
        while (um.canUndo()) {
            um.undo(lm);
            count++;
        }

        assertTrue(count >= 45 && count <= 55,
            "Expected compacted undo count between 45 and 55, got " + count);
    }

    @Test @SmallTest
    void multiLayer_undoRestoresLayerCount() {
        LayerManager lm = new LayerManager(50, 50);
        UndoManager um = new UndoManager(100);

        // Save with 1 layer
        um.saveState(lm);

        // Add a second layer (now 2 layers)
        lm.addLayer();
        assertEquals(2, lm.getLayerCount());

        // Undo should restore to 1 layer
        um.undo(lm);
        assertEquals(1, lm.getLayerCount());
        assertTrue(um.canRedo());

        // Redo should restore to 2 layers
        um.redo(lm);
        assertEquals(2, lm.getLayerCount());
    }

    @Test @SmallTest
    void clear_resetsAll() {
        LayerManager lm = new LayerManager(50, 50);
        UndoManager um = new UndoManager(100);

        um.saveState(lm);
        um.clear();

        assertFalse(um.canUndo());
        assertFalse(um.canRedo());
    }
}
