package com.opencheps.vision;

public class MissedMoveRecoveryTest {
    private static int assertions = 0;
    private static char[][] start() {
        String[] ranks = {
            "rnbqkbnr", "pppppppp", "........", "........",
            "........", "........", "PPPPPPPP", "RNBQKBNR"
        };
        char[][] out = new char[8][8];
        for (int r = 0; r < 8; r++) out[r] = ranks[r].toCharArray();
        return out;
    }
    private static char[][] copy(char[][] b) {
        char[][] out = new char[8][8];
        for (int r=0;r<8;r++) out[r] = b[r].clone();
        return out;
    }
    private static void move(char[][] b, String from, String to) {
        int sr = 8 - (from.charAt(1)-'0'), sc = from.charAt(0)-'a';
        int tr = 8 - (to.charAt(1)-'0'), tc = to.charAt(0)-'a';
        b[tr][tc] = b[sr][sc]; b[sr][sc] = '.';
    }
    private static char[][] flip(char[][] b) {
        char[][] out = new char[8][8];
        for (int r=0;r<8;r++) for (int c=0;c<8;c++)
            out[r][c] = b[7-r][7-c];
        return out;
    }
    private static void check(boolean actual, boolean expected, String label) {
        assertions++;
        if (actual != expected) throw new AssertionError(label + " actual=" + actual
                + " expected=" + expected);
    }
    public static void main(String[] args) {
        char[][] p = start();
        char[][] current = copy(p);
        move(current, "e2", "e4"); move(current, "d7", "d5");
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,current,true,true),true,
                "1.e4 d5 screenshot must recover");

        check(MissedMoveRecovery.matchesTwoQuietMoves(flip(p),flip(current),true,false),
                true,"black-at-bottom orientation");
        // For independent quiet moves the final board is the same in either
        // order. Preserve the *known* turn; the board alone cannot infer it.
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,current,false,true),true,
                "commuting moves cannot reveal their order");

        char[][] knights = copy(p);
        move(knights,"g1","f3"); move(knights,"g8","f6");
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,knights,true,true),true,
                "knight moves");
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,knights,true,false),true,
                "knight jumps are orientation-independent");

        char[][] partial = copy(p);
        move(partial,"e2","e4");
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,partial,true,true),false,
                "one-ply is not two-ply");

        char[][] impossible = copy(p);
        move(impossible,"e2","e5"); move(impossible,"d7","d5");
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,impossible,true,true),false,
                "pawn cannot move 3 squares");

        char[][] corrupt = copy(current); corrupt[4][4]='N';
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,corrupt,true,true),false,
                "classifier corruption not forced");

        char[][] unchanged = copy(p);
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,unchanged,true,true),false,
                "no change");

        char[][] beforeBlack = copy(p);
        move(beforeBlack,"e2","e4");
        char[][] afterBlack = copy(beforeBlack);
        move(afterBlack,"d7","d5"); move(afterBlack,"g1","f3");
        check(MissedMoveRecovery.matchesTwoQuietMoves(beforeBlack,afterBlack,false,true),
                true,"black then white");

        char[][] sameSide = copy(p);
        move(sameSide,"e2","e4"); move(sameSide,"g1","f3");
        check(MissedMoveRecovery.matchesTwoQuietMoves(p,sameSide,true,true),false,
                "two white moves must reject");

        // A capture changes a different pattern of squares; it must not be
        // mistaken for two quiet moves.
        char[][] capturesBefore = copy(current);
        char[][] capturesAfter = copy(capturesBefore);
        capturesAfter[3][3] = capturesAfter[4][4];
        capturesAfter[4][4] = '.';
        check(MissedMoveRecovery.matchesTwoQuietMoves(capturesBefore,capturesAfter,true,true),
                false,"capture must reject");
        System.out.println("MissedMoveRecovery: " + assertions + " cases passed");
    }
}
