#!/usr/bin/env python3
"""Make OpenCheps classification resilient to highlighted squares and small board drift.

Apply after patch_runtime.py and patch_recovery.py; fail if pinned upstream changed.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
SAMPLER = ROOT / "app/src/main/java/com/opencheps/vision/SquareSampler.java"
CLASSIFIER = ROOT / "app/src/main/java/com/opencheps/vision/PieceClassifier.java"
SERVICE = ROOT / "app/src/main/java/com/opencheps/capture/ScreenCaptureService.java"

def one(src, old, new, label):
    n = src.count(old)
    if n != 1:
        raise ValueError(f"{label}: expected 1 instance, found {n}")
    return src.replace(old, new, 1)

def main():
    src = SAMPLER.read_text(encoding="utf-8")
    src = one(src,
        """    public static float lumDistance(float[] a, float[] b) {""",
        """    /**
     * Compare SHAPES rather than raw cell brightness. Chess sites highlight
     * the last move by changing the entire square's background color, which
     * can make a stationary piece appear to change. Center each luminance
     * vector independently to remove its uniform brightness component.
     *
     * Distance still grows when a figure is added/removed or its contour
     * changes; callers additionally verify moves and whole-board legality.
     */
    public static float shapeDistance(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        if (n == 0) return Float.MAX_VALUE;
        float sumA = 0f, sumB = 0f;
        for (int i = 0; i < n; i++) {
            sumA += a[i];
            sumB += b[i];
        }
        float delta = (sumA - sumB) / n;
        float dist = 0f;
        for (int i = 0; i < n; i++) {
            float d = (a[i] - b[i]) - delta;
            dist += d * d;
        }
        return dist;
    }

    public static float lumDistance(float[] a, float[] b) {""",
        "shapeDistance method")
    SAMPLER.write_text(src, encoding="utf-8")

    src = CLASSIFIER.read_text(encoding="utf-8")
    src = one(src,
        """float d = SquareSampler.lumDistance(samples[i].feature, samples[j].feature);""",
        """float d = SquareSampler.shapeDistance(samples[i].feature, samples[j].feature);""",
        "bootstrap highlight-invariant noise")

    src = one(src,
        """frameChangeThreshold = Math.max(0.3f, maxBootstrapNoise * 2.0f + 0.1f);""",
        """// Clamp threshold so broad screenshot theme changes do not
        // mark dozens of stationary figures as moved.
        frameChangeThreshold = Math.max(0.22f,
                Math.min(0.75f, maxBootstrapNoise * 1.5f + 0.10f));""",
        "bootstrap shape threshold")

    src = one(src,
        """float fd = SquareSampler.lumDistance(
                            samples[idx].feature, previousFrame[idx].feature);""",
        """float fd = SquareSampler.shapeDistance(
                            samples[idx].feature, previousFrame[idx].feature);""",
        "shape-based static cell tracking")

    src = one(src,
        """        float distWhite = Math.abs(s.meanLum - whitePieceLumAvg);
        float distBlack = Math.abs(s.meanLum - blackPieceLumAvg);
        boolean isWhitePiece = distWhite < distBlack;

        boolean light = ((r + f) & 1) == 0;
        float best = Float.MAX_VALUE;
        char bestC = isWhitePiece ? 'P' : 'p';
        for (int i = 0; i < templates.length; i++) {
            char tc = labels[i];
            if (tc == '.') continue;
            if (Character.isUpperCase(tc) != isWhitePiece) continue;
            boolean tplLight = (((i / 8) + (i % 8)) & 1) == 0;
            if (tplLight != light) continue;
            float d = SquareSampler.lumDistance(s.feature, templates[i].feature);
            if (d < best) { best = d; bestC = tc; }
        }
        return bestC;""",
        """        // Highlight colors can significantly alter mean square brightness,
        // so do not determine piece side from meanLum alone. Match the
        // luminance shape against BOTH colors' learned piece templates.
        boolean light = ((r + f) & 1) == 0;
        float best = Float.MAX_VALUE;
        char bestC = '.';
        for (int i = 0; i < templates.length; i++) {
            char tc = labels[i];
            if (tc == '.') continue;
            boolean tplLight = (((i / 8) + (i % 8)) & 1) == 0;
            if (tplLight != light) continue;
            float d = SquareSampler.shapeDistance(s.feature, templates[i].feature);
            if (d < best) { best = d; bestC = tc; }
        }
        return bestC;""",
        "template matching invariant to cell brightness")
    CLASSIFIER.write_text(src, encoding="utf-8")

    src = SERVICE.read_text(encoding="utf-8")
    src = one(src,
        """    private BoardDetector.Board lastKnownBoard;
    private List<ArrowOverlayView.Arrow> currentArrows""",
        """    private BoardDetector.Board lastKnownBoard;
    private BoardDetector.Board pendingRelocation;
    private int pendingRelocationFrames;
    private List<ArrowOverlayView.Arrow> currentArrows""",
        "board stability state")

    src = one(src,
        """        lastPvs.clear();
    }

    private final Runnable sampleLoop""",
        """        lastPvs.clear();
        lastKnownBoard = null;
        pendingRelocation = null;
        pendingRelocationFrames = 0;
    }

    private final Runnable sampleLoop""",
        "reset board stability state")

    src = one(src,
        """            BoardDetector.Board board = BoardDetector.locate(bmp);

            if (board == null) {""",
        """            BoardDetector.Board detection = BoardDetector.locate(bmp);

            if (detection == null) {""",
        "rename board candidate")

    src = one(src,
        """            lostBoardCount = 0;
            lastKnownBoard = board;
            Rect screenBounds = boardBoundsOnScreen(board);""",
        """            lostBoardCount = 0;
            // Reject single-frame misdetections and small jitter caused by
            // piece movement and color highlights, which otherwise appear
            // as false changes on many squares.
            BoardDetector.Board board = stabilizeBoard(detection);
            if (board == null) {
                OverlayService.clearArrowsOnly();
                currentArrows.clear();
                lastPvs.clear();
                lastDisplayedArrowFen = "";
                lastEngineFen = "";
                panelStatus("Board moving · stabilizing");
                return;
            }
            lastKnownBoard = board;
            Rect screenBounds = boardBoundsOnScreen(board);""",
        "stable board sampling")

    src = one(src,
        """    private void drainReader() {""",
        """    /**
     * Keep a fixed pixel grid during ordinary play. A new board location
     * requires two consistent samples (e.g. after scrolling or rotation).
     */
    private BoardDetector.Board stabilizeBoard(BoardDetector.Board found) {
        if (lastKnownBoard == null) {
            pendingRelocation = null;
            pendingRelocationFrames = 0;
            return found;
        }
        int tolerance = Math.max(2, Math.round(lastKnownBoard.square * 0.16f));
        if (Math.abs(found.left - lastKnownBoard.left) <= tolerance
                && Math.abs(found.top - lastKnownBoard.top) <= tolerance
                && Math.abs(found.side - lastKnownBoard.side) <= tolerance) {
            pendingRelocation = null;
            pendingRelocationFrames = 0;
            return lastKnownBoard;
        }

        if (pendingRelocation != null
                && Math.abs(found.left - pendingRelocation.left) <= tolerance
                && Math.abs(found.top - pendingRelocation.top) <= tolerance
                && Math.abs(found.side - pendingRelocation.side) <= tolerance) {
            pendingRelocationFrames++;
        } else {
            pendingRelocation = found;
            pendingRelocationFrames = 1;
        }
        if (pendingRelocationFrames < 2) return null;
        pendingRelocation = null;
        pendingRelocationFrames = 0;
        // This is a genuine board relocation; classifier samples can
        // adapt to new coordinates without inventing any move.
        return found;
    }

    private void drainReader() {""",
        "stable detector helper")
    SERVICE.write_text(src, encoding="utf-8")

    print("Patched shape-normalized recognition and jitter-resistant board tracking.")

if __name__ == "__main__":
    main()
