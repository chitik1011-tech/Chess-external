package com.opencheps.bridge;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.opencheps.R;
import com.opencheps.ui.MainActivity;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * On-device companion for the browser extension. No MediaProjection.
 * Only listens on IPv4 loopback; a persistent pairing code is required.
 * The secure overlay covers JUST the chessboard, not the whole display.
 */
public final class BrowserBridgeService extends Service {
    public static final int PORT = 8765;
    public static final String ACTION_STOP="com.opencheps.bridge.STOP";
    private static final String TAG="Chess/BrowserBridge";
    private static final String CH="chess_bridge";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket server;
    private Thread netThread;
    private WindowManager wm;
    private BrowserHintView hint;
    private WindowManager.LayoutParams lp;
    private long lastFrameMs;
    private final Runnable expire = new Runnable() {
        @Override public void run() {
            if (!running.get()) return;
            if (hint != null && System.currentTimeMillis()-lastFrameMs > 2600L) hide();
            main.postDelayed(this,600);
        }
    };

    public static String token(android.content.Context c) {
        android.content.SharedPreferences p=c.getSharedPreferences("browser_bridge",MODE_PRIVATE);
        String t=p.getString("pair_code",null);
        if(t != null && t.matches("[0-9a-f]{12}")) return t;
        byte[] b=new byte[6];
        new SecureRandom().nextBytes(b);
        StringBuilder s=new StringBuilder();
        for(byte v:b)s.append(String.format(Locale.US,"%02x",v&0xff));
        t=s.toString();
        p.edit().putString("pair_code",t).apply();
        return t;
    }

    @Nullable @Override public IBinder onBind(Intent intent) {return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!Settings.canDrawOverlays(this)) {
            Log.e(TAG,"Overlay permission not granted");
            stopSelf();
            return START_NOT_STICKY;
        }
        startNotice();
        if(running.compareAndSet(false,true)) {
            wm=(WindowManager)getSystemService(WINDOW_SERVICE);
            netThread=new Thread(this::listen,"chess-browser-loopback");
            netThread.start();
            main.post(expire);
        }
        return START_STICKY;
    }

    private void startNotice() {
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH,"Chess browser bridge",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent launch=PendingIntent.getActivity(this,1,
                new Intent(this,MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CH)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Chess External — связь с браузером")
                .setContentText("127.0.0.1:"+PORT+" · только локальное подключение")
                .setOngoing(true).setContentIntent(launch).setPriority(NotificationCompat.PRIORITY_LOW);
        if(Build.VERSION.SDK_INT>=34) {
            startForeground(9202,b.build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(9202,b.build());
    }

    private void listen() {
        try(ServerSocket s=new ServerSocket(PORT,12,InetAddress.getByName("127.0.0.1"))) {
            server=s;
            while(running.get()) {
                try(Socket sock=s.accept()) {
                    sock.setSoTimeout(1800);
                    handle(sock);
                }catch(Exception e) {
                    if(running.get()) Log.w(TAG,"Bridge client: "+e.getMessage());
                }
            }
        }catch(Exception e) {
            Log.e(TAG,"Cannot bind bridge port",e);
            main.post(this::stopSelf);
        }
    }

    private static String readLine(BufferedInputStream in,int limit) throws Exception {
        ByteArrayOutputStream buf=new ByteArrayOutputStream();
        int b;
        while((b=in.read())>=0 && b!=10) {
            if(b!=13)buf.write(b);
            if(buf.size()>limit) throw new IllegalArgumentException("Too long header");
        }
        if(b<0)throw new IllegalArgumentException("Unexpected EOF");
        return buf.toString("UTF-8");
    }

    private static void reply(OutputStream out,int code,String msg) throws Exception {
        byte[] body=("{\"ok\":"+ (code==200?"true":"false") +
                ",\"message\":\""+msg+"\"}").getBytes(StandardCharsets.UTF_8);
        String status=code==200?"OK":code==403?"Forbidden":code==404?"Not Found":"Bad Request";
        String head="HTTP/1.1 "+code+" "+status+"\r\n"
                +"Content-Type: application/json; charset=utf-8\r\n"
                +"Access-Control-Allow-Origin: *\r\n"
                +"Access-Control-Allow-Headers: Content-Type\r\n"
                +"Cache-Control: no-store\r\nConnection: close\r\n"
                +"Content-Length: "+body.length+"\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private void handle(Socket sock) throws Exception {
        BufferedInputStream in=new BufferedInputStream(sock.getInputStream());
        OutputStream out=sock.getOutputStream();
        String req=readLine(in,512);
        if("GET /health HTTP/1.1".equals(req)) {
            // Restrict body unauthenticated: just advertise that a bridge exists.
            reply(out,200,"bridge-online");return;
        }
        if(!req.startsWith("POST /frame HTTP/1.")) {
            reply(out,404,"unsupported");return;
        }
        int length=-1;
        String origin="",type="";
        for(int n=0;n<50;n++) {
            String head=readLine(in,2048);
            if(head.isEmpty())break;
            int sep=head.indexOf(':');if(sep<0)continue;
            String k=head.substring(0,sep).trim().toLowerCase(Locale.ROOT);
            String v=head.substring(sep+1).trim();
            if("content-length".equals(k))length=Integer.parseInt(v);
            if("content-type".equals(k))type=v;
            if("origin".equals(k))origin=v;
        }
        // A normal chess website may not issue bridge commands; only
        // our privileged extension background worker can send them.
        if(!origin.isEmpty() && !origin.startsWith("chrome-extension://")
                && !origin.startsWith("moz-extension://")) {
            reply(out,403,"extension-required");return;
        }
        if(!type.startsWith("application/json")||length<2||length>8192) {
            reply(out,400,"invalid-json-size");return;
        }
        byte[] body=new byte[length];int done=0;
        while(done<length) {
            int n=in.read(body,done,length-done);
            if(n<0)break;
            done+=n;
        }
        if(done!=length){reply(out,400,"incomplete");return;}
        JSONObject msg=new JSONObject(new String(body,StandardCharsets.UTF_8));
        byte[] supplied=msg.optString("token","").getBytes(StandardCharsets.US_ASCII);
        byte[] expected=token(this).getBytes(StandardCharsets.US_ASCII);
        if(!MessageDigest.isEqual(supplied,expected)) {
            reply(out,403,"bad-code");return;
        }
        JSONObject b=msg.optJSONObject("board");
        if(b==null){reply(out,400,"board-missing");return;}
        String move=msg.optString("move","");
        if(!move.isEmpty()&&!move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
            reply(out,400,"move-invalid");return;
        }
        final boolean flipped=msg.optBoolean("flipped",false);
        final String mode=msg.optString("mode","arrow");
        final int pct=msg.optInt("quality",100);
        final String event=msg.optString("event","");
        final int speed=msg.optInt("speed",100);
        final float x=(float)b.optDouble("x",0);
        final float y=(float)b.optDouble("y",0);
        final float width=(float)b.optDouble("width",0);
        final float height=(float)b.optDouble("height",0);
        final float viewportW=(float)b.optDouble("viewportW",0);
        final String nextMove=move;
        if(!Float.isFinite(width)||!Float.isFinite(height)||!Float.isFinite(x)
                ||!Float.isFinite(y)||viewportW<100||width<90||height<90
                ||width>viewportW*1.2f) {
            reply(out,400,"geometry-invalid");return;
        }
        main.post(()->show(nextMove,mode,pct,flipped,event,speed,
                x,y,width,height,viewportW));
        reply(out,200,"accepted");
    }

    private void show(String move,String mode,int quality,boolean flipped,String event,
                      int speed,float bx,float by,float bw,float bh,float viewportW) {
        if(!running.get()) return;
        lastFrameMs=System.currentTimeMillis();
        if(move.isEmpty()) {hide();return;}
        DisplayMetrics metrics=new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        float ratio=metrics.widthPixels/viewportW;
        int left=Math.round(bx*ratio);
        int top=Math.round(by*ratio);
        int w=Math.round(bw*ratio);
        int h=Math.round(bh*ratio);
        int shiftDp=getSharedPreferences("browser_bridge",MODE_PRIVATE)
                .getInt("offset_y_dp",0);
        top+=Math.round(shiftDp*metrics.density);
        left=Math.max(0,Math.min(metrics.widthPixels-32,left));
        top=Math.max(0,Math.min(metrics.heightPixels-32,top));
        w=Math.min(metrics.widthPixels-left,Math.max(16,w));
        h=Math.min(metrics.heightPixels-top,Math.max(16,h));
        if(w<100||h<100){hide();return;}
        if(hint==null) {
            hint=new BrowserHintView(this);
            lp=new WindowManager.LayoutParams(w,h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_SECURE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity=Gravity.TOP|Gravity.START;
            lp.x=left;lp.y=top;
            try{wm.addView(hint,lp);}catch(Exception e){Log.e(TAG,"overlay failed",e);hint=null;return;}
        }else if(lp.x!=left||lp.y!=top||lp.width!=w||lp.height!=h){
            lp.x=left;lp.y=top;lp.width=w;lp.height=h;
            try{wm.updateViewLayout(hint,lp);}catch(Exception e){Log.w(TAG,"resize failed",e);}
        }
        hint.update(move,mode,quality,flipped,event,speed);
    }

    private void hide() {
        if(hint!=null){
            try{wm.removeView(hint);}catch(Exception ignored){}
            hint=null;lp=null;
        }
    }

    @Override public void onDestroy() {
        running.set(false);
        main.removeCallbacks(expire);
        try{if(server!=null)server.close();}catch(Exception ignored){}
        if(netThread!=null)netThread.interrupt();
        hide();
        super.onDestroy();
    }
}
