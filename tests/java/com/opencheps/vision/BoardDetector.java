package com.opencheps.vision;
/** JVM-only test stub. Do not package in Android. */
public final class BoardDetector {
    public static final class Board {
        public final int left,top,side;
        public final float square;
        public Board(int left,int top,int side) {
            this.left=left;this.top=top;this.side=side;this.square=side/8f;
        }
    }
}
