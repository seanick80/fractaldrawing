package com.seanick80.drawingapp.fractal;

/**
 * Parameters for an L-System tree, derived from fractal iteration statistics.
 */
public class LSystemParams {

    private final float angle;         // branch angle in degrees
    private final float lengthDecay;   // length multiplier per generation (0.5-0.9)
    private final int depth;           // number of L-System generations (4-8)
    private final float branchProb;    // probability of branching (0.5-1.0)
    private final int ruleVariant;     // which production rule set to use

    public LSystemParams(float angle, float lengthDecay, int depth,
                          float branchProb, int ruleVariant) {
        this.angle = angle;
        this.lengthDecay = lengthDecay;
        this.depth = depth;
        this.branchProb = branchProb;
        this.ruleVariant = ruleVariant;
    }

    public float getAngle() { return angle; }
    public float getLengthDecay() { return lengthDecay; }
    public int getDepth() { return depth; }
    public float getBranchProb() { return branchProb; }
    public int getRuleVariant() { return ruleVariant; }

    @Override
    public String toString() {
        return String.format("LSystem[angle=%.1f° decay=%.2f depth=%d prob=%.2f rule=%d]",
                angle, lengthDecay, depth, branchProb, ruleVariant);
    }
}
