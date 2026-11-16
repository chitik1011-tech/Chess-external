#!/usr/bin/env python3
"""Patch pinned OpenCheps with conservative two-ply resynchronization and full-width board alignment."""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
CAPTURE = ROOT / "app/src/main/java/com/opencheps/capture/ScreenCaptureService.java"
DETECTOR = ROOT / "app/src/main/java/com/opencheps/vision/BoardDetector.java"

def swap_once(source, old, new, label):
    count = source.count(old)
    if count != 1: raise ValueError(f"{label}: expected 1 occurrence; got {count}")
    return source.replace(old, new, 1)

def main():
    content = CAPTURE.read_text(encoding="utf-8")
    content = swap_once(content,
        "import com.opencheps.vision.MoveAnalyzer;\n",
        "import com.opencheps.vision.MoveAnalyzer;\nimport com.opencheps.vision.MissedMoveRecovery;\n",
        "import helper")
    content = swap_once(content,
        '''            MoveAnalyzer.Result m = MoveAnalyzer.analyze(confirmedGrid, grid);
            Log.i(TAG, "Move attempt: kind=" + m.kind + " mover=" + m.mover
                    + " changes=" + m.changeCount);

            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {''',
        '''            MoveAnalyzer.Result m = MoveAnalyzer.analyze(confirmedGrid, grid);
            Log.i(TAG, "Move attempt: kind=" + m.kind + " mover=" + m.mover
                    + " changes=" + m.changeCount);

            if ((m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null)
                    && MissedMoveRecovery.matchesTwoQuietMoves(
                            confirmedGrid, grid,
                            observedStm == Side.WHITE,
                            classifier.bootstrapSide() == Side.WHITE)) {
                // Validate full position before accepting the reconstructed pair.
                char[][] canonical = classifier.bootstrapSide() == Side.BLACK
                        ? FenBuilder.flip(grid) : grid;
                FenBuilder.Validation recovered = FenBuilder.validate(canonical);
                if (recovered.ok) {
                    confirmedGrid = grid;
                    classifier.commitConfirmed(confirmedGrid, bmp, board);
                    candidateGrid = null;
                    candidateRepeats = 0;
                    // Two alternating plies leave the same side to move.
                    lastEngineFen = "";
                    lastDisplayedArrowFen = "";
                    currentArrows.clear();
                    lastPvs.clear();
                    OverlayService.clearArrowsOnly();
                    panelStatus("Recovered 2 moves · " + observedStm + " to move");
                    if (observedStm == userSide) {
                        queryEngineAndRender(board, bmp);
                    } else {
                        panelEval("");
                    }
                    return;
                }
            }

            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {''',
        "recover two-move position")
    CAPTURE.write_text(content, encoding="utf-8")

    content = DETECTOR.read_text(encoding="utf-8")
    content = swap_once(content,
        '''        int left = Math.round(refined.x / scale);
        int top  = Math.round(refined.y / scale);
        int side = Math.round(refined.side / scale);
        return new Board(left, top, side, refined.score);
    }''',
        '''        int left = Math.round(refined.x / scale);
        int top  = Math.round(refined.y / scale);
        int side = Math.round(refined.side / scale);

        // On phones, many chess boards occupy 100% of the viewport width.
        // The grid search uses whole downsampled cells, so its maximum board
        // can be up to ~one square smaller than a true edge-to-edge board.
        // Snap ONLY likely full-width boards; preserve windowed boards.
        if (W0 <= H0 && side >= W0 * 0.90f && left <= W0 * 0.065f
                && Math.abs(left + side - W0) <= W0 * 0.11f) {
            top = findTopEdge(src, top, W0);
            left = 0;
            side = W0;
        }
        return new Board(left, top, side, refined.score);
    }

    /**
     * Refine top boundary by finding a horizontal brightness discontinuity.
     * Search only near the chess detector's top edge to avoid snapping to
     * rank boundaries or the app toolbar. The board may fill phone width.
     */
    private static int findTopEdge(Bitmap src, int approxTop, int boardSide) {
        int radius = Math.max(3, boardSide / 23);
        int yMin = Math.max(3, approxTop - radius);
        int yMax = Math.min(src.getHeight() - boardSide, approxTop + radius);
        if (yMax < yMin) return approxTop;

        float strongest = 0f;
        int best = approxTop;
        for (int y = yMin; y <= yMax; y++) {
            float score = 0;
            for (int i = 0; i < 16; i++) {
                int x = Math.min(src.getWidth() - 1,
                        Math.max(0, Math.round((i + 0.5f) * boardSide / 16f)));
                int above = src.getPixel(x, y - 2);
                int below = src.getPixel(x, y + 2);
                int ar = (above >>> 16) & 255, ag = (above >>> 8) & 255;
                int ab = above & 255;
                int br = (below >>> 16) & 255, bg = (below >>> 8) & 255;
                int bb = below & 255;
                score += (Math.abs(ar - br) + Math.abs(ag - bg)
                        + Math.abs(ab - bb)) / (16f * 3f);
            }
            if (score > strongest) { strongest = score; best = y; }
        }
        return strongest >= 23f ? best : approxTop;
    }''',
        "full-width detector correction")
    DETECTOR.write_text(content, encoding="utf-8")
    print("Applied multi-ply recovery and full-width geometry patches.")

if __name__ == "__main__":
    main()
