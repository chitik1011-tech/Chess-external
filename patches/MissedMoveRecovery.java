package com.opencheps.vision;

/**
 * Conservative recovery when exactly two non-capturing moves (one by each side)
 * occurred between screen samples. Never invents a turn from material count,
 * assumes castling rights, or trusts partial board observations.
 *
 * The 8x8 inputs use SCREEN order, top-to-bottom/left-to-right. whiteBottom
 * matches the board orientation established by PieceClassifier.bootstrapSide().
 */
public final class MissedMoveRecovery {
    private MissedMoveRecovery() {}

    /** Validate one quiet move using geometry and king-safety checks. */
    public static boolean matchesOneQuietMove(
            char[][] before, char[][] after, boolean whiteToMove, boolean whiteBottom) {
        if (!validShape(before) || !validShape(after)) return false;
        int[] from = null, to = null;
        int changes = 0;
        for (int r=0; r<8; r++) for (int c=0; c<8; c++) {
            char a = normalized(before[r][c]), b = normalized(after[r][c]);
            if (a == b) continue;
            if (++changes > 2) return false;
            if (a != '.' && b == '.') {
                if (from != null) return false;
                from = new int[]{r,c};
            } else if (a == '.' && b != '.') {
                if (to != null) return false;
                to = new int[]{r,c};
            } else return false;
        }
        if (changes != 2 || from == null || to == null) return false;
        char moving = normalized(before[from[0]][from[1]]);
        if (Character.isUpperCase(moving) != whiteToMove
                || moving != normalized(after[to[0]][to[1]])) return false;
        char[][] mid = copy(before);
        return playQuiet(mid,from,to,whiteToMove,whiteBottom) && equal(mid,after);
    }

    public static boolean matchesTwoQuietMoves(
            char[][] before, char[][] after, boolean whiteToMove, boolean whiteBottom) {
        if (!validShape(before) || !validShape(after)) return false;
        // Each side must vacate exactly one source and fill exactly one
        // destination. Captures/promotions/castling are intentionally excluded.
        int[][] src = {{-1, -1}, {-1, -1}};
        int[][] dst = {{-1, -1}, {-1, -1}};
        int changes = 0;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char a = normalized(before[r][c]), b = normalized(after[r][c]);
            if (a == b) continue;
            if (++changes > 4) return false;
            if (a != '.' && b == '.') {
                int side = Character.isUpperCase(a) ? 0 : 1;
                if (src[side][0] >= 0) return false;
                src[side][0] = r; src[side][1] = c;
            } else if (a == '.' && b != '.') {
                int side = Character.isUpperCase(b) ? 0 : 1;
                if (dst[side][0] >= 0) return false;
                dst[side][0] = r; dst[side][1] = c;
            } else {
                return false;
            }
        }
        if (changes != 4) return false;
        for (int side = 0; side < 2; side++) {
            if (src[side][0] < 0 || dst[side][0] < 0) return false;
            if (before[src[side][0]][src[side][1]]
                    != after[dst[side][0]][dst[side][1]]) return false;
        }
        char[][] mid = copy(before);
        int first = whiteToMove ? 0 : 1;
        if (!playQuiet(mid, src[first], dst[first], first == 0, whiteBottom)) return false;
        int second = 1 - first;
        if (!playQuiet(mid, src[second], dst[second], second == 0, whiteBottom)) return false;
        return equal(mid, after);
    }

    private static boolean playQuiet(
            char[][] b, int[] from, int[] to, boolean white, boolean whiteBottom) {
        int sr = from[0], sc = from[1], tr = to[0], tc = to[1];
        char p = normalized(b[sr][sc]);
        if (p == '.' || Character.isUpperCase(p) != white
                || normalized(b[tr][tc]) != '.') return false;

        int dr = tr - sr, dc = tc - sc;
        int ar = Math.abs(dr), ac = Math.abs(dc);
        boolean legal;
        switch (Character.toLowerCase(p)) {
            case 'p':
                int step = (white == whiteBottom) ? -1 : 1;
                int initial = (step == -1) ? 6 : 1;
                legal = dc == 0 && (dr == step
                        || (dr == 2 * step && sr == initial
                        && normalized(b[sr + step][sc]) == '.'));
                // A pawn cannot stay a pawn on its promotion rank.
                if (tr == 0 || tr == 7) legal = false;
                break;
            case 'n':
                legal = (ar == 1 && ac == 2) || (ar == 2 && ac == 1);
                break;
            case 'b':
                legal = ar > 0 && ar == ac && freeLine(b, sr, sc, tr, tc);
                break;
            case 'r':
                legal = (dr == 0) != (dc == 0) && freeLine(b, sr, sc, tr, tc);
                break;
            case 'q':
                legal = (ar == ac && ar > 0 || (dr == 0) != (dc == 0))
                        && freeLine(b, sr, sc, tr, tc);
                break;
            case 'k':
                legal = Math.max(ar, ac) == 1;
                break;
            default:
                return false;
        }
        if (!legal) return false;
        b[tr][tc] = p; b[sr][sc] = '.';
        return !kingAttacked(b, white, whiteBottom);
    }

    private static boolean kingAttacked(char[][] b, boolean white, boolean whiteBottom) {
        int kr = -1, kc = -1;
        char king = white ? 'K' : 'k';
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++)
            if (b[r][c] == king) { kr = r; kc = c; }
        if (kr < 0) return true;
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            char p = normalized(b[r][c]);
            if (p == '.' || Character.isUpperCase(p) == white) continue;
            int dr = kr - r, dc = kc - c, ar = Math.abs(dr), ac = Math.abs(dc);
            switch (Character.toLowerCase(p)) {
                case 'p':
                    int step = ((!white) == whiteBottom) ? -1 : 1;
                    if (dr == step && ac == 1) return true;
                    break;
                case 'n':
                    if (ar == 1 && ac == 2 || ar == 2 && ac == 1) return true;
                    break;
                case 'b':
                    if (ar > 0 && ar == ac && freeLine(b, r, c, kr, kc)) return true;
                    break;
                case 'r':
                    if ((dr == 0) != (dc == 0) && freeLine(b, r, c, kr, kc)) return true;
                    break;
                case 'q':
                    if (((ar > 0 && ar == ac) || ((dr == 0) != (dc == 0)))
                            && freeLine(b, r, c, kr, kc)) return true;
                    break;
                case 'k':
                    if (Math.max(ar, ac) == 1) return true;
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private static boolean freeLine(char[][] b, int sr, int sc, int tr, int tc) {
        int rStep = Integer.compare(tr, sr), cStep = Integer.compare(tc, sc);
        int r = sr + rStep, c = sc + cStep;
        while (r != tr || c != tc) {
            if (normalized(b[r][c]) != '.') return false;
            r += rStep; c += cStep;
        }
        return true;
    }

    private static char normalized(char p) { return p == 0 ? '.' : p; }
    private static boolean validShape(char[][] b) {
        if (b == null || b.length != 8) return false;
        for (char[] row : b) if (row == null || row.length != 8) return false;
        return true;
    }
    private static char[][] copy(char[][] b) {
        char[][] out = new char[8][8];
        for (int i = 0; i < 8; i++) System.arraycopy(b[i], 0, out[i], 0, 8);
        return out;
    }
    private static boolean equal(char[][] a, char[][] b) {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++)
            if (normalized(a[r][c]) != normalized(b[r][c])) return false;
        return true;
    }
}
