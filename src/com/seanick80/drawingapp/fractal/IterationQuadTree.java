package com.seanick80.drawingapp.fractal;

/**
 * A quadtree that caches fractal iteration counts in complex-plane coordinate space.
 * Each leaf stores a single sample point (real, imag) and its iteration count.
 * Lookups use a tolerance (Chebyshev distance) to find "close enough" cached values.
 */
public class IterationQuadTree {

    public static final int CACHE_MISS = -1;
    private static final int MAX_DEPTH = 50;
    private static final int MAX_SIZE = 5_000_000;

    private Node root;
    private int size;
    private int hits;
    private int lookups;

    public IterationQuadTree(double minReal, double maxReal, double minImag, double maxImag) {
        double centerR = (minReal + maxReal) / 2;
        double centerI = (minImag + maxImag) / 2;
        double halfSize = Math.max(maxReal - minReal, maxImag - minImag) / 2;
        root = new Node(centerR, centerI, halfSize);
    }

    public int size() { return size; }

    public int getHits() { return hits; }
    public int getLookups() { return lookups; }

    public void resetStats() {
        hits = 0;
        lookups = 0;
    }

    public void clear() {
        double cr = root.centerReal, ci = root.centerImag, hs = root.halfSize;
        root = new Node(cr, ci, hs);
        size = 0;
        hits = 0;
        lookups = 0;
    }

    /**
     * Look up a cached iteration count near (real, imag) within the given tolerance.
     * Returns the iteration count on hit, or CACHE_MISS (-1) on miss.
     */
    public int lookup(double real, double imag, double tolerance) {
        lookups++;
        int result = lookup(root, real, imag, tolerance);
        if (result != CACHE_MISS) hits++;
        return result;
    }

    private int lookup(Node node, double real, double imag, double tolerance) {
        if (node == null) return CACHE_MISS;

        // If the query point is outside this node's region (with tolerance margin), skip
        if (real < node.centerReal - node.halfSize - tolerance ||
            real > node.centerReal + node.halfSize + tolerance ||
            imag < node.centerImag - node.halfSize - tolerance ||
            imag > node.centerImag + node.halfSize + tolerance) {
            return CACHE_MISS;
        }

        if (node.hasData) {
            // Leaf with data — check Chebyshev distance
            if (Math.abs(real - node.pointReal) <= tolerance &&
                Math.abs(imag - node.pointImag) <= tolerance) {
                return node.iterationCount;
            }
            return CACHE_MISS;
        }

        // Internal node — descend into the correct quadrant first for speed
        int quadrant = node.quadrantFor(real, imag);
        Node[] children = { node.child(quadrant) };
        int result = lookup(children[0], real, imag, tolerance);
        if (result != CACHE_MISS) return result;

        // Check other quadrants if the point is near a boundary
        for (int q = 0; q < 4; q++) {
            if (q == quadrant) continue;
            result = lookup(node.child(q), real, imag, tolerance);
            if (result != CACHE_MISS) return result;
        }
        return CACHE_MISS;
    }

    /**
     * Insert a computed iteration count for the given complex coordinate.
     */
    public void insert(double real, double imag, int iterationCount) {
        if (size >= MAX_SIZE) return; // hard cap
        root = insert(root, real, imag, iterationCount, 0);
    }

    private Node insert(Node node, double real, double imag, int iterationCount, int depth) {
        if (node == null) {
            // Should not happen with proper root, but just in case
            return null;
        }

        // If this node is empty (no data, no children), store here as leaf
        if (!node.hasData && !node.hasChildren()) {
            node.pointReal = real;
            node.pointImag = imag;
            node.iterationCount = iterationCount;
            node.hasData = true;
            size++;
            return node;
        }

        // If leaf with data, subdivide and re-insert both points
        if (node.hasData) {
            if (depth >= MAX_DEPTH) return node; // can't subdivide further

            double oldR = node.pointReal, oldI = node.pointImag;
            int oldIter = node.iterationCount;
            node.hasData = false;

            // Re-insert the existing point
            insertIntoChild(node, oldR, oldI, oldIter, depth);
            // Insert the new point
            insertIntoChild(node, real, imag, iterationCount, depth);
            return node;
        }

        // Internal node — descend into the correct child
        insertIntoChild(node, real, imag, iterationCount, depth);
        return node;
    }

    private void insertIntoChild(Node node, double real, double imag, int iterationCount, int depth) {
        int q = node.quadrantFor(real, imag);
        Node child = node.child(q);
        if (child == null) {
            child = node.createChild(q);
        }
        insert(child, real, imag, iterationCount, depth + 1);
    }

    /**
     * Remove all cached points outside the given region (with margin).
     */
    public void pruneOutside(double minReal, double maxReal, double minImag, double maxImag) {
        size = prune(root, minReal, maxReal, minImag, maxImag);
    }

    private int prune(Node node, double minR, double maxR, double minI, double maxI) {
        if (node == null) return 0;

        // If the node's entire region is outside the prune bounds, clear it
        if (node.centerReal + node.halfSize < minR ||
            node.centerReal - node.halfSize > maxR ||
            node.centerImag + node.halfSize < minI ||
            node.centerImag - node.halfSize > maxI) {
            node.clearSubtree();
            return 0;
        }

        if (node.hasData) {
            if (node.pointReal < minR || node.pointReal > maxR ||
                node.pointImag < minI || node.pointImag > maxI) {
                node.hasData = false;
                return 0;
            }
            return 1;
        }

        int count = 0;
        count += prune(node.nw, minR, maxR, minI, maxI);
        count += prune(node.ne, minR, maxR, minI, maxI);
        count += prune(node.sw, minR, maxR, minI, maxI);
        count += prune(node.se, minR, maxR, minI, maxI);
        return count;
    }

    private static class Node {
        final double centerReal, centerImag;
        final double halfSize;

        // Leaf data
        double pointReal, pointImag;
        int iterationCount;
        boolean hasData;

        // Children
        Node nw, ne, sw, se;

        Node(double centerReal, double centerImag, double halfSize) {
            this.centerReal = centerReal;
            this.centerImag = centerImag;
            this.halfSize = halfSize;
        }

        boolean hasChildren() {
            return nw != null || ne != null || sw != null || se != null;
        }

        /** Returns quadrant index: 0=NW, 1=NE, 2=SW, 3=SE */
        int quadrantFor(double real, double imag) {
            boolean east = real >= centerReal;
            boolean south = imag >= centerImag;
            if (!east && !south) return 0; // NW
            if (east && !south) return 1;  // NE
            if (!east) return 2;           // SW
            return 3;                      // SE
        }

        Node child(int quadrant) {
            return switch (quadrant) {
                case 0 -> nw;
                case 1 -> ne;
                case 2 -> sw;
                case 3 -> se;
                default -> null;
            };
        }

        Node createChild(int quadrant) {
            double qs = halfSize / 2; // child half-size
            Node c = switch (quadrant) {
                case 0 -> nw = new Node(centerReal - qs, centerImag - qs, qs);
                case 1 -> ne = new Node(centerReal + qs, centerImag - qs, qs);
                case 2 -> sw = new Node(centerReal - qs, centerImag + qs, qs);
                case 3 -> se = new Node(centerReal + qs, centerImag + qs, qs);
                default -> null;
            };
            return c;
        }

        void clearSubtree() {
            hasData = false;
            nw = ne = sw = se = null;
        }
    }
}
