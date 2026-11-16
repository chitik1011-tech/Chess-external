package com.opencheps.vision;
import android.graphics.Bitmap;

public final class ForegroundOccupancyTest {
    private static final int SIDE=640,CELL=80;
    private static final class Image extends Bitmap {
        private final int[] pixels=new int[SIDE*SIDE];
        @Override public int getWidth(){return SIDE;}
        @Override public int getHeight(){return SIDE;}
        @Override public int getPixel(int x,int y){return pixels[y*SIDE+x];}
        @Override public void getPixels(int[] into,int offset,int stride,
                                        int x,int y,int w,int h) {
            for(int r=0;r<h;r++) System.arraycopy(pixels,(y+r)*SIDE+x,into,
                    offset+r*stride,w);
        }
    }
    private static void check(boolean cond,String why) {
        if(!cond)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        // Synthetic 1.e4 d5: marked yellow sources/destinations and a
        // different background on alternating squares, as in Checkmate.
        String[] rows={"rnbqkbnr","ppp.pppp","........","...p....",
                       "....P...","........","PPPP.PPP","RNBQKBNR"};
        Image img=new Image();
        int backgroundDark=0xFFAE876A,backgroundLight=0xFFEBDDC1,
            yellow=0xFFF1E244;
        for(int y=0;y<SIDE;y++)for(int x=0;x<SIDE;x++) {
            int r=y/CELL,c=x/CELL;
            boolean marked=(r==1&&c==3)||(r==3&&c==3)
                    ||(r==4&&c==4)||(r==6&&c==4);
            int pixel=marked?yellow:((r+c)%2==0?backgroundLight:backgroundDark);
            int xx=x%CELL-40,yy=y%CELL-40;
            char piece=rows[r].charAt(c);
            if(piece!='.' && xx*xx/(20f*20f)+yy*yy/(24f*24f)<1f) {
                pixel=Character.isUpperCase(piece)?0xFFFFFFFF:0xFF333333;
                if(Character.isUpperCase(piece)
                        && xx*xx/(19f*19f)+yy*yy/(23f*23f)>=1f)
                    pixel=0xFF222222;
            }
            img.pixels[y*SIDE+x]=pixel;
        }
        boolean[][] observed=ForegroundOccupancy.read(
                img,new BoardDetector.Board(0,0,SIDE));
        check(observed!=null,"color-independent occupancy must not be ambiguous");
        int checks=0;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++) {
            boolean expected=rows[r].charAt(c)!='.';
            check(observed[r][c]==expected,
                "screen cell "+r+","+c+" expected "+expected);
            checks++;
        }
        System.out.println("ForegroundOccupancy: "+checks+" squares verified");
    }
}
