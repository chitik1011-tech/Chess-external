package com.opencheps.bridge;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Only the chessboard-sized area is a protected overlay window. */
public final class BrowserHintView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String move = "", mode = "arrow", event = "";
    private int quality = 100;
    private boolean flipped;
    private long effectStart;
    private int effectDuration = 1000;
    private boolean promo;

    public BrowserHintView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void update(String nextMove, String nextMode, int nextQuality,
                       boolean nextFlipped, String nextEvent, int speed) {
        move = nextMove == null ? "" : nextMove;
        mode = "neon".equals(nextMode) ? "neon" : "arrow";
        quality = Math.max(0, Math.min(100, nextQuality));
        flipped = nextFlipped;
        promo = move.length() == 5;
        if (nextEvent != null && !nextEvent.isEmpty()) {
            event = nextEvent;
            effectStart = SystemClock.uptimeMillis();
            effectDuration = Math.max(300, Math.min(2500, 100000 / Math.max(60,speed)));
        }
        invalidate();
    }

    private float[] center(String cell) {
        int f = cell.charAt(0) - 'a';
        int rank = cell.charAt(1) - '1';
        int x = flipped ? 7-f : f;
        int y = flipped ? rank : 7-rank;
        return new float[]{(x+0.5f)*getWidth()/8f, (y+0.5f)*getHeight()/8f};
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (!move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) return;
        final float[] from = center(move.substring(0,2));
        final float[] to = center(move.substring(2,4));
        final float cell = getWidth()/8f;
        float x=to[0], y=to[1];

        if ("neon".equals(mode)) {
            drawSquare(c,from[0],from[1],cell,0xFF46C7FF);
            drawSquare(c,x,y,cell,0xFF46FFC0);
        } else {
            paint.reset(); paint.setAntiAlias(true);
            paint.setColor(0xFF42FFD0);
            paint.setStrokeWidth(Math.max(5f,cell*0.11f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShadowLayer(14f,0,0,0xCC28FFD2);
            c.drawLine(from[0],from[1],x,y,paint);
            paint.clearShadowLayer();
            double ang=Math.atan2(y-from[1],x-from[0]);
            float tail=Math.max(13f,cell*.31f);
            Path triangle=new Path();
            triangle.moveTo(x,y);
            triangle.lineTo(x-(float)Math.cos(ang-.42)*tail,
                            y-(float)Math.sin(ang-.42)*tail);
            triangle.lineTo(x-(float)Math.cos(ang+.42)*tail,
                            y-(float)Math.sin(ang+.42)*tail);
            triangle.close();
            paint.setStyle(Paint.Style.FILL);
            c.drawPath(triangle,paint);
        }

        String label = quality + "%" + (promo ? "  =" + Character.toUpperCase(move.charAt(4)) : "");
        paint.reset();paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",1));
        paint.setTextSize(Math.max(18f,cell*.28f));
        float tw=paint.measureText(label);
        float bx=Math.max(tw/2+10,Math.min(getWidth()-tw/2-10,x));
        float by=Math.max(30f,y-cell*.54f);
        paint.setColor(0xE60A2426);
        c.drawRoundRect(new RectF(bx-tw/2-10,by-22,bx+tw/2+10,by+6),8,8,paint);
        paint.setColor(Color.WHITE);
        c.drawText(label,bx-tw/2,by,paint);

        long elapsed=SystemClock.uptimeMillis()-effectStart;
        if (!event.isEmpty() && elapsed<effectDuration) {
            float progress = elapsed / (float)effectDuration;
            paint.reset();paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            int alpha=Math.max(0,(int)((1-progress)*220));
            paint.setColor((alpha<<24) | ("capture".equals(event)?0x00FF66BF:0x00FFE178));
            float radius=cell*(.22f+1.6f*progress);
            c.drawCircle(x,y,radius,paint);
            paint.setStyle(Paint.Style.FILL);
            if("mate".equals(event)) {
                paint.setTextSize(cell*.8f);
                paint.setColor((alpha<<24)|0x00FFE5B0);
                c.drawText("♔",x-cell*.29f,y-cell*(.3f+progress*.9f),paint);
            }
            postInvalidateDelayed(16);
        } else event="";
    }

    private void drawSquare(Canvas c,float x,float y,float size,int color) {
        paint.reset();paint.setAntiAlias(true);
        paint.setColor((color&0x00FFFFFF)|0x44000000);
        paint.setStyle(Paint.Style.FILL);
        float pad=size*.12f;
        RectF area=new RectF(x-size/2+pad,y-size/2+pad,
                             x+size/2-pad,y+size/2-pad);
        c.drawRoundRect(area,8f,8f,paint);
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(3f,size*.075f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setShadowLayer(10f,0,0,color);
        c.drawRoundRect(area,8f,8f,paint);
        paint.clearShadowLayer();
    }
}
