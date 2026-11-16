package com.opencheps.vision;

public class MixedMoveRecoveryTest {
    private static int checked;
    private static char[][] start(){
        String[] s={"rnbqkbnr","pppppppp","........","........",
                    "........","........","PPPPPPPP","RNBQKBNR"};
        char[][] a=new char[8][8];
        for(int i=0;i<8;i++)a[i]=s[i].toCharArray();
        return a;
    }
    private static char[][] copy(char[][] b){
        char[][] a=new char[8][8];
        for(int i=0;i<8;i++)a[i]=b[i].clone();
        return a;
    }
    private static void move(char[][] b,String s,String d){
        int sr=8-(s.charAt(1)-'0'),sc=s.charAt(0)-'a';
        int dr=8-(d.charAt(1)-'0'),dc=d.charAt(0)-'a';
        b[dr][dc]=b[sr][sc];b[sr][sc]='.';
    }
    private static char[][] flip(char[][] b){
        char[][] out=new char[8][8];
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)out[7-r][7-c]=b[r][c];
        return out;
    }
    private static void check(int actual,int expected,String label){
        if(actual!=expected)throw new AssertionError(label+": got "+actual+" expected "+expected);
        checked++;
    }
    public static void main(String[] args){
        char[][] start=start();
        char[][] e4=copy(start);
        move(e4,"e2","e4");
        check(MixedMoveRecovery.matchingPlies(start,e4,true,true),1,"e2e4");
        char[][] d5=copy(e4);move(d5,"d7","d5");
        check(MixedMoveRecovery.matchingPlies(start,d5,true,true),2,"e4 d5");
        check(MixedMoveRecovery.matchingPlies(flip(start),flip(d5),true,false),2,
                "black orientation");
        char[][] captured=copy(d5);
        move(captured,"e4","d5");
        check(MixedMoveRecovery.matchingPlies(d5,captured,true,true),1,
                "e4 takes d5");
        char[][] mixed=copy(start);
        move(mixed,"g1","f3");
        move(mixed,"d7","d5");
        // Construct a midgame snapshot with white knight f3 and enemy queen g5.
        mixed[0][3]='.';mixed[3][6]='q';
        char[][] after=copy(mixed);
        move(after,"f3","g5"); // knight takes the queen
        move(after,"f7","f6"); // pawn moves
        check(MixedMoveRecovery.matchingPlies(mixed,after,true,true),2,
                "Nxg5 f6 (capture and quiet, missed intermediate screen)");
        // Independent moves commute: the final position cannot establish
        // which player moved first without observing the intermediate frame.
        check(MixedMoveRecovery.matchingPlies(mixed,after,false,true),2,
                "commuting capture and pawn push");
        check(MixedMoveRecovery.matchingPlies(start,e4,false,true),0,
                "single pawn move by wrong side");
        char[][] corrupt=copy(after);corrupt[6][1]='Q';
        check(MixedMoveRecovery.matchingPlies(mixed,corrupt,true,true),0,
                "wrongly classified pawn must not be accepted");
        char[][] impossible=copy(start);
        move(impossible,"e2","e5");
        check(MixedMoveRecovery.matchingPlies(start,impossible,true,true),0,
                "pawn three squares");
        char[][] same=copy(start);
        check(MixedMoveRecovery.matchingPlies(start,same,true,true),0,
                "unchanged");
        // Promotion and castling require special rights, not inferred here.
        System.out.println("MixedMoveRecovery: "+checked+" tests passed");
    }
}
