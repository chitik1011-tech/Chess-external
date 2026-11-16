package com.opencheps.vision;

import android.graphics.Bitmap;

/**
 * Screen-geometry-based occupancy reader. Unlike RGB template matching,
 * estimate the local square background from its corners and detect
 * the figure silhouette in the central region. Handles uniform yellow
 * move highlights without learning their color in advance.
 *
 * Ambiguous cells cause null and do NOT trigger a guessed move.
 */
public final class ForegroundOccupancy {
    private ForegroundOccupancy() {}
    private static final int GRID = 16;
    private static final float MIN_PRESENT = 0.19f;
    private static final float MAX_ABSENT = 0.10f;
    private static final int RGB_DIFFERENCE = 44;

    public static boolean[][] read(Bitmap bmp, BoardDetector.Board board) {
        if (bmp == null || board == null || board.square < 16) return null;
        boolean[][] occupied = new boolean[8][8];
        int width = bmp.getWidth(), height = bmp.getHeight();
        float side = board.square;
        for (int r=0;r<8;r++) for(int c=0;c<8;c++) {
            float left = board.left + c * side;
            float top = board.top + r * side;
            if (left < 0 || top < 0 || left + side > width + 0.5f
                    || top + side > height + 0.5f) return null;

            int[] red = new int[4], green = new int[4], blue = new int[4];
            float[] corners = {0.09f,0.91f};
            int k=0;
            for(float y:corners) for(float x:corners) {
                int pixel = safePixel(bmp, Math.round(left + x*side),
                        Math.round(top + y*side));
                red[k] = (pixel >>> 16) & 255;
                green[k] = (pixel >>> 8) & 255;
                blue[k] = pixel & 255;
                k++;
            }
            int bgR = median4(red), bgG = median4(green), bgB = median4(blue);
            int foregroundCount=0;
            for(int y=0;y<GRID;y++) for(int x=0;x<GRID;x++) {
                float dx = 0.17f + (x / (float)(GRID-1)) * 0.66f;
                float dy = 0.17f + (y / (float)(GRID-1)) * 0.66f;
                int pixel=safePixel(bmp, Math.round(left + dx*side),
                        Math.round(top + dy*side));
                int dr=Math.abs(((pixel >>> 16)&255)-bgR);
                int dg=Math.abs(((pixel >>> 8)&255)-bgG);
                int db=Math.abs((pixel&255)-bgB);
                if(Math.max(dr,Math.max(dg,db)) > RGB_DIFFERENCE) foregroundCount++;
            }
            float ratio=foregroundCount / (float)(GRID*GRID);
            if (ratio > MAX_ABSENT && ratio < MIN_PRESENT) return null;
            occupied[r][c] = ratio >= MIN_PRESENT;
        }
        return occupied;
    }

    private static int safePixel(Bitmap bmp,int x,int y) {
        return bmp.getPixel(Math.min(bmp.getWidth()-1,Math.max(0,x)),
                Math.min(bmp.getHeight()-1,Math.max(0,y)));
    }
    private static int median4(int[] a) {
        int[] b=a.clone();
        java.util.Arrays.sort(b);
        return (b[1]+b[2])/2;
    }
}
