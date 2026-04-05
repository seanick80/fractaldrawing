package com.seanick80.drawingapp.fractal;

import com.seanick80.drawingapp.SmallTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuadTreeTest {

    @Test @SmallTest
    void insertAndLookup() {
        IterationQuadTree tree = new IterationQuadTree(-5, 5, -5, 5);
        tree.insert(1.0, 1.0, 42);
        tree.insert(-1.0, -1.0, 10);
        tree.insert(3.0, 3.0, 99);

        assertTrue(tree.size() >= 3, "Expected at least 3 entries");

        double tol = 0.01;
        assertEquals(42, tree.lookup(1.0, 1.0, tol));
        assertEquals(42, tree.lookup(1.0 + tol * 0.5, 1.0, tol));
        assertEquals(IterationQuadTree.CACHE_MISS, tree.lookup(2.0, 2.0, tol));
    }

    @Test @SmallTest
    void pruneOutside() {
        IterationQuadTree tree = new IterationQuadTree(-5, 5, -5, 5);
        tree.insert(1.0, 1.0, 42);
        tree.insert(-1.0, -1.0, 10);

        tree.pruneOutside(0.5, 1.5, 0.5, 1.5);

        assertEquals(42, tree.lookup(1.0, 1.0, 0.01));
        assertEquals(IterationQuadTree.CACHE_MISS, tree.lookup(-1.0, -1.0, 0.01));
    }

    @Test @SmallTest
    void lookupFull_returnsIterAndFinalZ() {
        IterationQuadTree tree = new IterationQuadTree(-5, 5, -5, 5);
        tree.insert(1.0, 1.0, 42, 0.5, 0.3);

        double tol = 0.01;
        IterationQuadTree.CacheResult hit = tree.lookupFull(1.0, 1.0, tol);
        assertNotNull(hit);
        assertEquals(42, hit.iterationCount);
        assertEquals(0.5, hit.finalZr, 1e-10);
        assertEquals(0.3, hit.finalZi, 1e-10);

        assertNull(tree.lookupFull(3.0, 3.0, tol));
    }

    @Test @SmallTest
    void largeScale_insertAndPrune() {
        IterationQuadTree tree = new IterationQuadTree(-3, 3, -3, 3);
        int count = 0;
        for (double r = -1.9; r <= 1.9; r += 0.1) {
            for (double i = -1.9; i <= 1.9; i += 0.1) {
                tree.insert(r, i, count++);
            }
        }

        assertTrue(tree.size() >= count, "Expected at least " + count + " entries");
        assertNotEquals(IterationQuadTree.CACHE_MISS, tree.lookup(0.0, 0.0, 0.1));

        tree.pruneOutside(-0.5, 0.5, -0.5, 0.5);
        assertNotEquals(IterationQuadTree.CACHE_MISS, tree.lookup(0.0, 0.0, 0.1));
        assertEquals(IterationQuadTree.CACHE_MISS, tree.lookup(1.5, 1.5, 0.01));
    }
}
