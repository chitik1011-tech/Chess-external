package com.opencheps.vision;

public final class OccupancyRecoveryTest {
    private static int tested=0;
    private static char[][] start() {
        String[] lines={"rnbqkbnr","pppppppp","........","........",
                        "........","........","PPPPPPPP","RNBQKBNR"};
        char[][] b=new char[8][8];
        for(int r=0;r<8;r++)b[r]=lines[r].toCharArray();
        return b;
    }
    private static char[][] copy(char[][] b) {
        char[][] c=new char[8][8];
        for(int r=0;r<8;r++)c[r]=b[r].clone();
        return c;
    }
    private static void move(char[][] b,String from,String to) {
        int fr=8-(from.charAt(1)-'0'),fc=from.charAt(0)-'a';
        int tr=8-(to.charAt(1)-'0'),tc=to.charAt(0)-'a';
        b[tr][tc]=b[fr][fc];b[fr][fc]='.';
    }
    private static boolean[][] mask(char[][] b) {
        boolean[][] m=new boolean[8][8];
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)m[r][c]=b[r][c]!='.';
        return m;
    }
    private static boolean equal(char[][] a,char[][] b) {
        if(a==null||b==null)return false;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)if(a[r][c]!=b[r][c])return false;
        return true;
    }
    private static void check(boolean a,String why) {
        if(!a)throw new AssertionError(why);
        tested++;
    }
    private static char[][] flip(char[][] b) {
        char[][] o=new char[8][8];
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)o[r][c]=b[7-r][7-c];
        return o;
    }
    public static void main(String[] args) {
        char[][] start=start();
        char[][] e4=copy(start);
        move(e4,"e2","e4");
        OccupancyRecovery.Result r1=OccupancyRecovery.recover(start,mask(e4),true,true);
        check(r1!=null && r1.plies==1 && equal(r1.grid,e4),"white e4 single");
        char[][] d5=copy(e4);
        move(d5,"d7","d5");
        OccupancyRecovery.Result r2=OccupancyRecovery.recover(start,mask(d5),true,true);
        check(r2!=null && r2.plies==2 && equal(r2.grid,d5),"1.e4 d5 two plies");
        OccupancyRecovery.Result r3=OccupancyRecovery.recover(e4,mask(d5),false,true);
        check(r3!=null && r3.plies==1 && equal(r3.grid,d5),"black d5 single");
        char[][] flippedStart=flip(start),flippedD5=flip(d5);
        OccupancyRecovery.Result rb=OccupancyRecovery.recover(
                flippedStart,mask(flippedD5),true,false);
        check(rb!=null && rb.plies==2 && equal(rb.grid,flippedD5),"black POV");
        check(OccupancyRecovery.recover(start,mask(start),true,true)==null,
                "no apparent move");
        char[][] impossible=copy(start);
        move(impossible,"e2","e5");move(impossible,"d7","d5");
        check(OccupancyRecovery.recover(start,mask(impossible),true,true)==null,
                "pawn moved three squares");
        check(OccupancyRecovery.recover(start,mask(e4),false,true)==null,
                "wrong side to move");
        char[][] bothWhite=copy(start);
        move(bothWhite,"e2","e4");move(bothWhite,"d2","d4");
        check(OccupancyRecovery.recover(start,mask(bothWhite),true,true)==null,
                "two moves by same side");
        boolean[][] noise=mask(d5);
        noise[0][0]=false;
        check(OccupancyRecovery.recover(start,noise,true,true)==null,
                "five unexpected changes must reject");
        System.out.println("OccupancyRecovery: "+tested+" checks passed");
    }
}
