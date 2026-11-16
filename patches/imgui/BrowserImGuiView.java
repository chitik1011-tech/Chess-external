package com.opencheps.bridge;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Transparent protected OpenGL ES 3 window rendered by REAL Dear ImGui
 * from native C++ (not an Android Canvas or a fake "imgui-like" View).
 *
 * Only the board-sized TYPE_APPLICATION_OVERLAY is used by the service.
 * Both the WindowManager window and this SurfaceView's child surface are
 * marked secure. Device capture behavior can still vary.
 */
public final class BrowserImGuiView extends GLSurfaceView {
    private static final String TAG = "Chess/ImGui";
    private static volatile boolean libraryAvailable;

    static {
        try {
            System.loadLibrary("chessimgui");
            libraryAvailable = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Missing ImGui native engine", e);
            libraryAvailable = false;
        }
    }

    private static final class Frame {
        final String move, mode, event;
        final int quality, speed;
        final boolean flipped;
        final long eventId;
        Frame(String move, String mode, int quality, boolean flipped,
              String event, int speed, long eventId) {
            this.move = move;
            this.mode = mode;
            this.quality = quality;
            this.flipped = flipped;
            this.event = event;
            this.speed = speed;
            this.eventId = eventId;
        }
    }

    private volatile Frame frame = new Frame("", "arrow", 100, false, "", 100, 0);
    private final Runnable animationTick = new Runnable() {
        @Override public void run() {
            if (!animating || !isAttachedToWindow()) return;
            if (SystemClock.uptimeMillis() >= animationStopMs) {
                animating = false;
                requestRender();
                return;
            }
            requestRender();
            postDelayed(this, 32);
        }
    };
    private boolean animating;
    private long animationStopMs;

    public BrowserImGuiView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        setSecure(true);

        setRenderer(new Renderer() {
            private boolean started;
            @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                if (!libraryAvailable) return;
                nativeInit();
                started = true;
            }

            @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
                if (started) nativeResize(width, height);
            }

            @Override public void onDrawFrame(GL10 gl) {
                if (!started) return;
                Frame f = frame;
                nativeSetFrame(f.move, f.mode, f.quality,
                        f.flipped, f.event, f.speed, f.eventId);
                nativeRender();
            }
        });
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void update(String move, String mode, int quality,
                       boolean flipped, String event, int speed) {
        String safeMove = move == null ? "" : move;
        String safeMode = "neon".equals(mode) ? "neon" : "arrow";
        String safeEvent = event == null ? "" : event;
        long id = safeEvent.isEmpty() ? frame.eventId : SystemClock.uptimeMillis();
        frame = new Frame(safeMove, safeMode, Math.max(0, Math.min(100, quality)),
                flipped, safeEvent, Math.max(60, Math.min(180, speed)), id);
        requestRender();
        if (!safeEvent.isEmpty()) {
            animationStopMs = SystemClock.uptimeMillis() + 3000;
            removeCallbacks(animationTick);
            animating = true;
            post(animationTick);
        }
    }

    private native void nativeInit();
    private native void nativeResize(int width, int height);
    private native void nativeSetFrame(String move, String mode, int quality,
                                      boolean flipped, String event, int speed, long eventId);
    private native void nativeRender();

    public static boolean isLibraryAvailable() { return libraryAvailable; }

    @Override protected void onDetachedFromWindow() {
        animating = false;
        removeCallbacks(animationTick);
        super.onDetachedFromWindow();
    }
}
