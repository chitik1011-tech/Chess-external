#!/usr/bin/env python3
"""Make Stockfish analysis non-blocking and prevent frame jitter from flickering hints.

Applies after existing OpenCheps patches and fails closed on upstream changes.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
SERVICE = ROOT / "app/src/main/java/com/opencheps/capture/ScreenCaptureService.java"

def once(s, old, new, label):
    n = s.count(old)
    if n != 1:
        raise ValueError(f"{label}: expected one match, got {n}")
    return s.replace(old, new, 1)

def main():
    s = SERVICE.read_text(encoding="utf-8")
    s = once(s,
        "import java.util.List;\n",
        "import java.util.List;\nimport java.util.concurrent.atomic.AtomicLong;\n",
        "atomic generation import")

    s = once(s,
        "    private boolean engineReady;",
        "    private volatile boolean engineReady;\n"
        "    private volatile boolean stopped;\n"
        "    private HandlerThread engineThread;\n"
        "    private Handler engineWorker;\n"
        "    private final AtomicLong analysisGeneration = new AtomicLong();",
        "separate Stockfish worker fields")

    s = once(s,
        """        worker.post(() -> {
            try {
                engine.start(getApplicationContext());
                engineReady = true;
                panelStatus("Engine ready · playing as " + userSide);
            } catch (Exception e) {
                Log.e(TAG, "engine start failed", e);
                panelStatus("Engine error: " + e.getMessage());
            }
        });""",
        """        // Stockfish may spend seconds on a position. Never block the
        // MediaProjection sampler: it must keep observing each player move.
        engineThread = new HandlerThread("oc-stockfish", Thread.NORM_PRIORITY - 1);
        engineThread.start();
        engineWorker = new Handler(engineThread.getLooper());
        engineWorker.post(() -> {
            try {
                engine.start(getApplicationContext());
                engineReady = true;
                panelStatus("Engine ready · playing as " + userSide);
            } catch (Exception e) {
                Log.e(TAG, "engine start failed", e);
                panelStatus("Engine error: " + e.getMessage());
            }
        });""",
        "non-blocking Stockfish initialization")

    s = once(s,
        """                        paused = isPaused;
                        if (isPaused) {""",
        """                        paused = isPaused;
                        analysisGeneration.incrementAndGet();
                        if (isPaused) {""",
        "cancel analysis on pause")

    s = once(s,
        """                    @Override public void onSideChange(Side newSide) {
                        userSide = newSide;""",
        """                    @Override public void onSideChange(Side newSide) {
                        analysisGeneration.incrementAndGet();
                        userSide = newSide;""",
        "cancel on side change")

    s = once(s,
        """    private void resetGameState() {
        confirmedGrid = null;""",
        """    private void resetGameState() {
        analysisGeneration.incrementAndGet();
        confirmedGrid = null;""",
        "cancel on recalibration")

    s = once(s,
        """            // The displayed arrow belongs to the last position, not this one.
            // Hide it immediately, even when recognition is noisy or ambiguous.
            OverlayService.clearArrowsOnly();
            currentArrows.clear();
            lastPvs.clear();
            lastDisplayedArrowFen = "";
            lastEngineFen = "";
            panelEval("");

            if (!gridsEqual(grid, candidateGrid)) {""",
        """            // Do not invalidate Stockfish merely because the IMAGE classifier
            // twitched. Its output can change when yellow last-move highlights
            // animate. Require two matching observations before clearing.
            if (candidateRepeats >= 2) {
                OverlayService.clearArrowsOnly();
                currentArrows.clear();
                lastPvs.clear();
                lastDisplayedArrowFen = "";
                panelEval("");
            }
            if (!gridsEqual(grid, candidateGrid)) {""",
        "debounce raw classifier fluctuations")

    s = once(s,
        """                        OverlayService.clearArrowsOnly();
                        currentArrows.clear();
                        lastPvs.clear();
                        lastDisplayedArrowFen = "";
                        lastEngineFen = "";
                        panelEval("");
                        panelStatus("Detected " + recovered.plies""",
        """                        // A candidate is not a confirmed new position yet.
                        // Keep the calculated result cached for the previous
                        // board so a transient highlight does not force a retry.
                        OverlayService.clearArrowsOnly();
                        panelEval("");
                        panelStatus("Detected " + recovered.plies""",
        "avoid clearing engine cache on tentative occupancy result")

    s = once(s,
        """            boolean[][] occupancy = ForegroundOccupancy.read(bmp, board);
            if (occupancy != null && confirmedGrid != null) {""",
        """            boolean[][] occupancy = ForegroundOccupancy.read(bmp, board);
            if (occupancy != null && confirmedGrid != null
                    && !occupancyMatches(occupancy, confirmedGrid)) {
                // Reliable foreground changes can represent actual piece moves.
                // Immediately hide the old arrow, without discarding the old
                // analysis result until a different board is CONFIRMED.
                OverlayService.clearArrowsOnly();
            }
            if (occupancy != null && confirmedGrid != null) {""",
        "hide old hints when foreground actually differs")

    insert_before = """            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {
                // Never assume that an uncertain move makes it the player's turn."""
    replacement = """            if (m.kind == MoveAnalyzer.Kind.UNKNOWN || m.mover == null) {
                // The original analyzer only understands ONE move between
                // frames. Search for one or two legal moves, including
                // captures, and accept ONLY an exact observed board match.
                int mixedPlies = MixedMoveRecovery.matchingPlies(
                        confirmedGrid, grid, observedStm == Side.WHITE,
                        classifier.bootstrapSide() == Side.WHITE);
                if (mixedPlies > 0) {
                    char[][] canonical = classifier.bootstrapSide() == Side.BLACK
                            ? FenBuilder.flip(grid) : grid;
                    FenBuilder.Validation check = FenBuilder.validate(canonical);
                    if (check.ok) {
                        confirmedGrid = grid;
                        classifier.commitConfirmed(confirmedGrid, bmp, board);
                        candidateGrid = null;
                        candidateRepeats = 0;
                        if (mixedPlies == 1) {
                            observedStm = observedStm == Side.WHITE
                                    ? Side.BLACK : Side.WHITE;
                        }
                        lastEngineFen = "";
                        lastDisplayedArrowFen = "";
                        currentArrows.clear();
                        lastPvs.clear();
                        OverlayService.clearArrowsOnly();
                        panelStatus("Recovered " + mixedPlies
                                + " move(s), capture supported");
                        if (observedStm == userSide) {
                            queryEngineAndRender(board, bmp);
                        } else {
                            panelEval("");
                        }
                        return;
                    }
                }
                // Never assume that an uncertain move makes it the player's turn."""
    s = once(s, insert_before, replacement, "recover captures before unclear warning")

    s = once(s,
        "import com.opencheps.vision.OccupancyRecovery;\n",
        "import com.opencheps.vision.OccupancyRecovery;\n"
        "import com.opencheps.vision.MixedMoveRecovery;\n",
        "mixed-recovery import")

    start = "    private void queryEngineAndRender(BoardDetector.Board board, Bitmap currentBitmap) {"
    end = "    private void repushCurrentArrows(BoardDetector.Board board) {"
    if s.count(start) != 1 or s.count(end) != 1:
        raise ValueError("queryEngineAndRender boundaries changed")
    a=s.index(start);b=s.index(end)
    replacement_method = """    private void queryEngineAndRender(BoardDetector.Board board, Bitmap currentBitmap) {
        if (stopped || paused || confirmedGrid == null || !engineReady
                || engineWorker == null || observedStm != userSide) return;
        char[][] canonical = classifier.bootstrapSide() == Side.BLACK
                ? FenBuilder.flip(confirmedGrid) : confirmedGrid;
        FenBuilder.Validation validation = FenBuilder.validate(canonical);
        if (!validation.ok) {
            panelStatus("Invalid position: " + validation.reason);
            return;
        }
        final String fen = FenBuilder.build(confirmedGrid,
                classifier.bootstrapSide(), userSide);
        if (fen == null) {
            panelStatus("FEN build failed");
            return;
        }
        // Keep a result across consecutive identical screen frames.
        if (fen.equals(lastEngineFen)) {
            if (fen.equals(lastDisplayedArrowFen)) repushCurrentArrows(board);
            return;
        }
        lastEngineFen = fen;
        final int think = Prefs.thinkMs(getApplicationContext());
        final int multiPv = Math.max(1, Math.min(3,
                Prefs.multiPv(getApplicationContext())));
        final Side requestedSide = userSide;
        final long generation = analysisGeneration.incrementAndGet();
        final BoardDetector.Board requestBoard = board;
        panelStatus("Thinking " + think + " ms…");

        // Cancel queued old requests (a running UCI query cannot be interrupted
        // safely, so its result is discarded via generation + FEN checks).
        engineWorker.removeCallbacksAndMessages(null);
        engineWorker.post(() -> {
            List<StockfishEngine.Pv> result = null;
            Exception error = null;
            long started = System.currentTimeMillis();
            try {
                result = engine.topMoves(fen, think, multiPv);
            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Stockfish query failed", e);
            }
            final List<StockfishEngine.Pv> moves = result;
            final Exception failure = error;
            final long duration = System.currentTimeMillis() - started;
            if (worker == null) return;
            worker.post(() -> {
                if (stopped || paused
                        || generation != analysisGeneration.get()
                        || observedStm != requestedSide || userSide != requestedSide
                        || confirmedGrid == null
                        || !fen.equals(fenOf(confirmedGrid))) return;
                if (failure != null) {
                    panelStatus("Stockfish error · recalibrate to retry");
                    return;
                }
                if (moves == null || moves.isEmpty()) {
                    panelStatus("No legal move");
                    return;
                }
                lastPvs = new ArrayList<>(moves);
                lastDisplayedArrowFen = fen;
                BoardDetector.Board drawBoard =
                        lastKnownBoard != null ? lastKnownBoard : requestBoard;

                // Do not draw an arrow while an unconfirmed image change is
                // in progress. Once the board stabilizes, cached result is
                // rendered without requesting Stockfish again.
                if (candidateGrid != null) {
                    OverlayService.clearArrowsOnly();
                    panelStatus("Checking board · analysis cached");
                    return;
                }
                currentArrows = buildArrows(lastPvs, drawBoard);
                OverlayService.pushArrows(currentArrows,
                        boardBoundsOnScreen(drawBoard));
                StockfishEngine.Pv best = moves.get(0);
                String score = best.mate
                        ? "M" + Math.abs(plyToMate(best.cp))
                        : String.format("%+.2f", best.cp / 100.0);
                panelEval("YOU (" + userSide + ") "
                        + formatUci(best.uci) + "  " + score);
                panelStatus("Your move (" + duration + " ms)");
            });
        });
    }

"""
    s=s[:a]+replacement_method+s[b:]

    s=once(s,
        """        try { if (engine != null) engine.stop(); } catch (Exception ignored) {}""",
        """        stopped = true;
        analysisGeneration.incrementAndGet();
        if (engineWorker != null) {
            engineWorker.removeCallbacksAndMessages(null);
            engineWorker.post(() -> {
                try { if (engine != null) engine.stop(); }
                catch (Exception e) { Log.w(TAG, "engine stop failed", e); }
            });
        }
        if (engineThread != null) engineThread.quitSafely();""",
        "safe engine shutdown")

    SERVICE.write_text(s,encoding="utf-8")
    print("Stockfish now runs on its own HandlerThread; screen capture remains responsive.")

if __name__ == "__main__":
    main()
