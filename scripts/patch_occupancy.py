#!/usr/bin/env python3
"""Patch pinned OpenCheps to recover positions using the visible occupancy pattern.

Apply after patch_runtime, patch_recovery and patch_classifier. Never generate
a hint from unmatched, ambiguous or illegal image recognition.
"""
from pathlib import Path
import sys

ROOT=Path(sys.argv[1]).resolve()
SERVICE=ROOT/"app/src/main/java/com/opencheps/capture/ScreenCaptureService.java"
def replace(source, old, new, label):
    found=source.count(old)
    if found!=1: raise ValueError(f"{label}: expected 1, found {found}")
    return source.replace(old,new,1)

def main():
    code=SERVICE.read_text(encoding="utf-8")
    code=replace(code,
        "import com.opencheps.vision.MissedMoveRecovery;\n",
        "import com.opencheps.vision.MissedMoveRecovery;\n"
        "import com.opencheps.vision.ForegroundOccupancy;\n"
        "import com.opencheps.vision.OccupancyRecovery;\n",
        "imports")

    code=replace(code,
        """            grid = repairWithMoveLogic(confirmedGrid, grid);

            if (gridsEqual(grid, confirmedGrid)) {""",
        """            grid = repairWithMoveLogic(confirmedGrid, grid);

            // Strong fallback for noisy square classifiers. A yellow move
            // highlight changes background RGB without moving a piece. Read
            // the foreground silhouette separately from the new background,
            // then reconstruct only legal, unambiguous quiet move(s).
            boolean[][] occupancy = ForegroundOccupancy.read(bmp, board);
            if (occupancy != null && confirmedGrid != null) {
                OccupancyRecovery.Result recovered =
                        OccupancyRecovery.recover(confirmedGrid, occupancy,
                                observedStm == Side.WHITE,
                                classifier.bootstrapSide() == Side.WHITE);

                if (recovered != null) {
                    if (!gridsEqual(recovered.grid, candidateGrid)) {
                        candidateGrid = recovered.grid;
                        candidateRepeats = 1;
                        OverlayService.clearArrowsOnly();
                        currentArrows.clear();
                        lastPvs.clear();
                        lastDisplayedArrowFen = "";
                        lastEngineFen = "";
                        panelEval("");
                        panelStatus("Detected " + recovered.plies
                                + " moves · confirming");
                        return;
                    }
                    candidateRepeats++;
                    if (candidateRepeats < 2) return;

                    char[][] canonical = classifier.bootstrapSide() == Side.BLACK
                            ? FenBuilder.flip(recovered.grid) : recovered.grid;
                    FenBuilder.Validation check = FenBuilder.validate(canonical);
                    if (!check.ok) {
                        panelStatus("Position rejected (" + check.reason + ")");
                        return;
                    }
                    confirmedGrid = recovered.grid;
                    classifier.commitConfirmed(confirmedGrid, bmp, board);
                    candidateGrid = null;
                    candidateRepeats = 0;
                    if (recovered.plies == 1) {
                        observedStm = observedStm == Side.WHITE
                                ? Side.BLACK : Side.WHITE;
                    }
                    lastEngineFen = "";
                    lastDisplayedArrowFen = "";
                    currentArrows.clear();
                    lastPvs.clear();
                    OverlayService.clearArrowsOnly();
                    panelStatus("Recovered " + recovered.plies + " move(s)");
                    if (observedStm == userSide) {
                        queryEngineAndRender(board, bmp);
                    } else {
                        panelEval("");
                        panelStatus(observedStm + "'s turn — waiting");
                    }
                    return;
                }

                // If the silhouettes show NO change at all, suppress the
                // false-positive label changes caused by square highlights
                // and avoid endlessly entering Position unclear.
                if (occupancyMatches(occupancy, confirmedGrid)) {
                    candidateGrid = null;
                    candidateRepeats = 0;
                    if (observedStm == userSide) {
                        if (!lastEngineFen.equals(fenOf(confirmedGrid))) {
                            queryEngineAndRender(board, bmp);
                        } else {
                            repushCurrentArrows(board);
                        }
                    } else {
                        OverlayService.clearArrowsOnly();
                    }
                    return;
                }
            }

            if (gridsEqual(grid, confirmedGrid)) {""",
        "occupancy-based fallback")

    code=replace(code,
        """    private boolean gridsEqual(char[][] a, char[][] b) {""",
        """    /** True only if every foreground-presence bit matches our state. */
    private boolean occupancyMatches(boolean[][] observed, char[][] grid) {
        if (observed == null || grid == null) return false;
        for (int r=0;r<8;r++) for (int c=0;c<8;c++) {
            if (observed[r][c] != (grid[r][c] != '.'
                    && grid[r][c] != 0)) return false;
        }
        return true;
    }

    private boolean gridsEqual(char[][] a, char[][] b) {""",
        "occupancy validation helper")

    SERVICE.write_text(code,encoding="utf-8")
    print("Applied background-independent occupancy recovery; invalid states remain blocked.")

if __name__=="__main__":
    main()
