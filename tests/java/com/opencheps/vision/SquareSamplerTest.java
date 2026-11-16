package com.opencheps.vision;

public class SquareSamplerTest {
    private static final int N = SquareSampler.N * SquareSampler.N;
    private static void assertNear(float actual, float expected, float eps, String why) {
        if (Math.abs(actual - expected) > eps)
            throw new AssertionError(why + ": expected " + expected + ", got " + actual);
    }
    private static void assertGreater(float a, float b, String why) {
        if (!(a > b)) throw new AssertionError(why + ": " + a + " <= " + b);
    }
    public static void main(String[] args) {
        float[] whitePawn = new float[N];
        float[] highlighted = new float[N];
        float[] changedFigure = new float[N];
        for (int y = 0; y < SquareSampler.N; y++)
            for (int x = 0; x < SquareSampler.N; x++) {
                int i = y * SquareSampler.N + x;
                // Piece-like mask surrounded by a flat square background.
                boolean mask = (x >= 6 && x <= 13 && y >= 5 && y <= 16);
                whitePawn[i] = mask ? 0.30f : 0.70f;
                highlighted[i] = whitePawn[i] + 0.10f;
                changedFigure[i] = (mask && x <= 8) ? 0.40f : 0.70f;
            }

        float old = SquareSampler.lumDistance(whitePawn, highlighted);
        float shape = SquareSampler.shapeDistance(whitePawn, highlighted);
        assertGreater(old, 2.0f, "Raw luminance is sensitive to a yellow highlight");
        assertNear(shape, 0.0f, 0.0001f, "Centered shape should ignore uniform tint");
        assertGreater(SquareSampler.shapeDistance(whitePawn, changedFigure), 0.5f,
            "Changing the piece silhouette must stay detectable");
        assertNear(SquareSampler.shapeDistance(whitePawn, whitePawn), 0f, 0.0001f,
            "No change means no movement");
        assertNear(SquareSampler.shapeDistance(new float[0], whitePawn),
            Float.MAX_VALUE, 1.0f, "Invalid features should not match");
        System.out.println("SquareSampler: 5 checks passed");
    }
}
