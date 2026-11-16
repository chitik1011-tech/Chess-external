package com.opencheps.vision;

/**
 * Reconstruct at most two QUIET moves from an occupancy-only observation.
 * The source piece labels are taken from the last CONFIRMED position,
 * never guessed from a single highlighted screenshot.
 *
 * This is intentionally fail-closed: no captures, promotions, castling,
 * en passant, or ambiguous matches.
 */
public final class OccupancyRecovery {
    private OccupancyRecovery() {}
    public static final class Result {
        public final char[][] grid;
        public final int plies;
        public Result(char[][] grid, int plies) {
            this.grid = grid;
            this.plies = plies;
        }
    }

    public static Result recover(
            char[][] before, boolean[][] seen, boolean whiteToMove, boolean whiteBottom) {
        if (before == null || seen == null || before.length != 8 || seen.length != 8)
            return null;
        int[][] sources = new int[2][2], targets = new int[2][2];
        int sourceCount = 0, targetCount = 0;
        for (int r=0; r<8; r++) {
            if (before[r] == null || before[r].length != 8
                    || seen[r] == null || seen[r].length != 8) return null;
            for (int c=0; c<8; c++) {
                char p = before[r][c] == 0 ? '.' : before[r][c];
                if (!"prnbqkPRNBQK.".contains(String.valueOf(p))) return null;
                boolean expected = p != '.';
                if (expected && !seen[r][c]) {
                    if (sourceCount == 2) return null;
                    sources[sourceCount++] = new int[]{r,c};
                } else if (!expected && seen[r][c]) {
                    if (targetCount == 2) return null;
                    targets[targetCount++] = new int[]{r,c};
                }
            }
        }

        if (sourceCount != targetCount || sourceCount == 0) return null;

        if (sourceCount == 1) {
            char moving = before[sources[0][0]][sources[0][1]];
            if (Character.isUpperCase(moving) != whiteToMove) return null;
            char[][] candidate = copy(before);
            candidate[sources[0][0]][sources[0][1]] = '.';
            candidate[targets[0][0]][targets[0][1]] = moving;
            return MissedMoveRecovery.matchesOneQuietMove(
                    before,candidate,whiteToMove,whiteBottom)
                    ? new Result(candidate,1) : null;
        }

        // Exactly two sources must belong to opposite sides.
        char first = before[sources[0][0]][sources[0][1]];
        char second = before[sources[1][0]][sources[1][1]];
        if (Character.isUpperCase(first) == Character.isUpperCase(second)) return null;
        Result result = null;
        for (int order = 0; order < 2; order++) {
            char[][] candidate = copy(before);
            for (int i=0; i<2; i++) {
                candidate[sources[i][0]][sources[i][1]] = '.';
                int[] destination = targets[i == 0 ? order : 1 - order];
                candidate[destination[0]][destination[1]] =
                        before[sources[i][0]][sources[i][1]];
            }
            if (!MissedMoveRecovery.matchesTwoQuietMoves(
                    before,candidate,whiteToMove,whiteBottom)) continue;
            if (result != null && !same(result.grid,candidate)) return null;
            result = new Result(candidate,2);
        }
        return result;
    }

    private static char[][] copy(char[][] src) {
        char[][] out = new char[8][8];
        for (int i=0;i<8;i++) out[i] = src[i].clone();
        return out;
    }
    private static boolean same(char[][] a,char[][] b) {
        for (int r=0;r<8;r++) for(int c=0;c<8;c++) if(a[r][c]!=b[r][c]) return false;
        return true;
    }
}
