package android.graphics;
/** Compile-only Android Bitmap stub for pure-Java SquareSampler shape tests. */
public abstract class Bitmap {
    public abstract int getWidth();
    public abstract int getHeight();
    public abstract void getPixels(int[] pixels, int offset, int stride,
                                   int x, int y, int width, int height);
}
