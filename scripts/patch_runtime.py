#!/usr/bin/env python3
"""Fail-closed source patch for OpenCheps capture/overlay fixes.

Upstream pinned in workflow. Every replacement requires an exact old fragment;
unexpected upstream changes should fail CI, never silently build broken code.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
SERVICE = ROOT / "app/src/main/java/com/opencheps/capture/ScreenCaptureService.java"
ARROWS = ROOT / "app/src/main/java/com/opencheps/overlay/ArrowOverlayView.java"


def replace_exact(text, old, new, label):
    found = text.count(old)
    if found != 1:
        raise ValueError(f"{label}: expected one match; found {found}")
    return text.replace(old, new, 1)


def main():
    src = SERVICE.read_text(encoding="utf-8")
    src = replace_exact(src,
        "    private List<ArrowOverlayView.Arrow> currentArrows = new ArrayList<>();",
        """    private List<ArrowOverlayView.Arrow> currentArrows = new ArrayList<>();
    // Store chess moves in board coordinates, not absolute pixels: boards can move.
    private List<StockfishEngine.Pv> lastPvs = new ArrayList<>();""",
        "PV cache field")

    src = replace_exact(src,
        """        currentArrows.clear();
    }

    private final Runnable sampleLoop""",
        """        currentArrows.clear();
        lastPvs.clear();
    }

    private final Runnable sampleLoop""",
        "reset cached moves")

    src = replace_exact(src,
        """            if (!gridsEqual(grid, candidateGrid)) {
                candidateGrid = grid;""",
        """            // The displayed arrow belongs to the last position, not this one.
            // Hide it immediately, even when recognition is noisy or ambiguous.
            OverlayService.clearArrowsOnly();
            currentArrows.clear();
            lastPvs.clear();
            lastDisplayedArrowFen = "";
            lastEngineFen = "";
            panelEval("");

            if (!gridsEqual(grid, candidateGrid)) {
                candidateGrid = grid;""",
        "clear arrows on visual change")

    src = replace_exact(src,
        """            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {
                if (candidateRepeats >= 5) {
                    char[][] forced = forceValidGrid(grid);
                    if (forced != null) {
                        confirmedGrid = forced;
                        classifier.commitConfirmed(confirmedGrid, bmp, board);
                        observedStm = userSide;
                        candidateGrid = null;
                        candidateRepeats = 0;
                        panelStatus("Forced re-sync");
                        if (observedStm == userSide) queryEngineAndRender(board, bmp);
                    } else {
                        panelStatus("Position unclear · " + m.changeCount + " changed");
                    }
                } else {
                    panelStatus("Unclear change · " + m.changeCount + " sq");
                }
                return;
            }""",
        """            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {
                // Never assume that an uncertain move makes it the player's turn.
                // Wait for a verified move or manual recalibration.
                panelStatus("Position unclear · " + m.changeCount
                        + " changed · recalibrate if persistent");
                return;
            }""",
        "remove unsafe forced resync")

    src = replace_exact(src,
        """    private char[][] forceValidGrid(char[][] grid) {
        FenBuilder.Validation v = FenBuilder.validate(
                classifier.bootstrapSide() == Side.BLACK ? FenBuilder.flip(grid) : grid);
        return v.ok ? grid : null;
    }

""",
        "",
        "remove forceValidGrid")

    src = replace_exact(src,
        """            currentArrows = buildArrows(pvs, board);
            lastDisplayedArrowFen = fen;""",
        """            lastPvs = new ArrayList<>(pvs);
            currentArrows = buildArrows(lastPvs, board);
            lastDisplayedArrowFen = fen;""",
        "save board-coordinate moves")

    src = replace_exact(src,
        """    private void repushCurrentArrows(BoardDetector.Board board) {
        if (currentArrows == null || currentArrows.isEmpty()) return;
        List<ArrowOverlayView.Arrow> rebuilt = rebuildArrowsForNewBounds(board);
        OverlayService.pushArrows(rebuilt, boardBoundsOnScreen(board));
    }

    private List<ArrowOverlayView.Arrow> rebuildArrowsForNewBounds(BoardDetector.Board board) {
        return currentArrows;
    }""",
        """    private void repushCurrentArrows(BoardDetector.Board board) {
        if (confirmedGrid == null || observedStm != userSide
                || lastDisplayedArrowFen.isEmpty()
                || !lastDisplayedArrowFen.equals(fenOf(confirmedGrid))
                || lastPvs.isEmpty()) {
            OverlayService.clearArrowsOnly();
            return;
        }
        // A board may move/resize between frames. Recompute every arrow endpoint.
        currentArrows = buildArrows(lastPvs, board);
        OverlayService.pushArrows(currentArrows, boardBoundsOnScreen(board));
    }""",
        "rebuild arrow endpoints after board moves")

    src = replace_exact(src,
        """            if (persp == Side.BLACK) {
                fx = 7 - fx; fy = 7 - fy; tx = 7 - tx; ty = 7 - ty;
            }
            float sx = ox""",
        """            if (persp == Side.BLACK) {
                fx = 7 - fx; fy = 7 - fy; tx = 7 - tx; ty = 7 - ty;
            }
            if (fx < 0 || fx > 7 || fy < 0 || fy > 7
                    || tx < 0 || tx > 7 || ty < 0 || ty > 7
                    || confirmedGrid == null) continue;
            // Block impossible hints. At minimum, the source square must hold
            // our own piece and the destination must not hold our own piece.
            char moving = confirmedGrid[fy][fx];
            char dest = confirmedGrid[ty][tx];
            if (moving == '.' || moving == 0
                    || (Character.isUpperCase(moving) != (userSide == Side.WHITE))
                    || (dest != '.' && dest != 0
                        && Character.isUpperCase(dest) == Character.isUpperCase(moving))) {
                continue;
            }
            float sx = ox""",
        "validate arrow source and destination")

    # A side toggle must also clear old arrows and cached moves.
    src = replace_exact(src,
        """                        } else {
                            lastEngineFen = "";
                            lastDisplayedArrowFen = "";
                            observedStm = newSide;
                            panelStatus("Now playing " + newSide);
                        }""",
        """                        } else {
                            lastEngineFen = "";
                            lastDisplayedArrowFen = "";
                            currentArrows.clear();
                            lastPvs.clear();
                            OverlayService.clearArrowsOnly();
                            observedStm = newSide;
                            panelStatus("Now playing " + newSide);
                        }""",
        "clear cache on side toggle")

    SERVICE.write_text(src, encoding="utf-8")
    overlay = ARROWS.read_text(encoding="utf-8")
    overlay = replace_exact(overlay,
        """    @Override protected void onDraw(Canvas c) {
        if (boardBounds != null) {""",
        """    @Override protected void onDraw(Canvas c) {
        // Incoming board coordinates are physical screen pixels. The overlay
        // canvas starts at the window's origin, which can be below the status
        // bar (e.g. by ~75 px on Android 15). Convert coordinates correctly.
        int[] location = new int[2];
        getLocationOnScreen(location);
        int save = c.save();
        c.translate(-location[0], -location[1]);
        if (boardBounds != null) {""",
        "align overlay to screen origin")
    end = """        }
    }
}
"""
    if not overlay.endswith(end):
        raise ValueError("Unexpected ArrowOverlayView ending")
    overlay = overlay[:-len(end)] + """        }
        c.restoreToCount(save);
    }
}
"""
    ARROWS.write_text(overlay, encoding="utf-8")
    print("Runtime fixes patched: stale arrows, safe turn detection, board relocation, screen origin.")


if __name__ == "__main__":
    main()
