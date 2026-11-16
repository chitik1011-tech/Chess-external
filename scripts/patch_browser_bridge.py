#!/usr/bin/env python3
"""Add a browser-extension bridge mode to the pinned OpenCheps Android source."""
from pathlib import Path
import sys

ROOT=Path(sys.argv[1]).resolve()
manifest=ROOT/"app/src/main/AndroidManifest.xml"
layout=ROOT/"app/src/main/res/layout/activity_main.xml"
activity=ROOT/"app/src/main/java/com/opencheps/ui/MainActivity.java"

def replace_one(s,old,new,label):
    n=s.count(old)
    if n!=1:raise ValueError(f"{label}: expected 1 match, found {n}")
    return s.replace(old,new,1)

def main():
    m=manifest.read_text(encoding="utf-8")
    m=replace_one(m,'    <uses-permission android:name="android.permission.WAKE_LOCK" />',
      '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n'
      '    <uses-permission android:name="android.permission.INTERNET" />\n'
      '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />',
      "permissions")
    m=replace_one(m,'        <service\n            android:name=".overlay.OverlayService"',
      '''        <service
            android:name=".bridge.BrowserBridgeService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="local_browser_chess_overlay" />
        </service>

        <service
            android:name=".overlay.OverlayService"''',"bridge manifest")
    manifest.write_text(m,encoding="utf-8")

    ui=layout.read_text(encoding="utf-8")
    ui=replace_one(ui,'        <Button\n            android:id="@+id/btnStart"',
      '''        <TextView
            android:text="Браузерный режим (без захвата экрана)"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="#FF85FFD7"
            android:layout_marginTop="22dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/bridgePairingCode"
            android:text="Код связи: —"
            android:textColor="#FFFFFFFF"
            android:textSize="16sp"
            android:textIsSelectable="true"
            android:paddingTop="12dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:text="Введи этот код в расширении v0.9. Подсказка появится в отдельном защищённом окне Android."
            android:textColor="#FFB0BEC5"
            android:textSize="12sp"
            android:paddingTop="5dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/btnBridgeStart"
            android:text="Включить связь с браузером"
            android:backgroundTint="#FF007D68"
            android:textColor="#FFFFFFFF"
            android:layout_marginTop="12dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/btnBridgeStop"
            android:text="Остановить связь"
            android:backgroundTint="#FF62443E"
            android:textColor="#FFFFFFFF"
            android:layout_marginTop="4dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/bridgeOffsetLabel"
            android:text="Поправка высоты: 0 dp"
            android:textColor="#FFCFEBE4"
            android:layout_marginTop="10dp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <SeekBar
            android:id="@+id/bridgeOffsetBar"
            android:max="600"
            android:progress="300"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <TextView
            android:id="@+id/bridgeStatus"
            android:text="Режим браузера выключен"
            android:textColor="#FFA5D6A7"
            android:textSize="12sp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content" />

        <Button
            android:id="@+id/btnStart"''',"browser UI")
    layout.write_text(ui,encoding="utf-8")

    src=activity.read_text(encoding="utf-8")
    src=replace_one(src,'import com.opencheps.capture.ScreenCaptureService;',
      'import com.opencheps.capture.ScreenCaptureService;\n'
      'import com.opencheps.bridge.BrowserBridgeService;', "activity import")
    src=replace_one(src,'        btnOverlay.setOnClickListener(v -> requestOverlay());',
      '''        final TextView bridgePair = findViewById(R.id.bridgePairingCode);
        final TextView bridgeStatus = findViewById(R.id.bridgeStatus);
        final TextView offsetLabel = findViewById(R.id.bridgeOffsetLabel);
        final SeekBar offsetBar = findViewById(R.id.bridgeOffsetBar);
        bridgePair.setText("Код связи: " + BrowserBridgeService.token(this));
        android.content.SharedPreferences bridgePrefs =
                getSharedPreferences("browser_bridge", MODE_PRIVATE);
        int initialOffset = bridgePrefs.getInt("offset_y_dp",0);
        offsetBar.setProgress(initialOffset+300);
        offsetLabel.setText("Поправка высоты: " + initialOffset + " dp");
        offsetBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar v,int p,boolean user) {
                int offset = p-300;
                offsetLabel.setText("Поправка высоты: " + offset + " dp");
                if (user) bridgePrefs.edit().putInt("offset_y_dp",offset).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar v) {}
            @Override public void onStopTrackingTouch(SeekBar v) {}
        });
        findViewById(R.id.btnBridgeStart).setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                toast("Сначала разреши отображение поверх других окон");
                requestOverlay();
                return;
            }
            Intent svc = new Intent(this, BrowserBridgeService.class);
            if (Build.VERSION.SDK_INT>=26) startForegroundService(svc);
            else startService(svc);
            bridgeStatus.setText("Сервер 127.0.0.1:8765 · введи код в расширении");
            toast("Связь с браузером включена");
        });
        findViewById(R.id.btnBridgeStop).setOnClickListener(v -> {
            stopService(new Intent(this,BrowserBridgeService.class));
            bridgeStatus.setText("Режим браузера выключен");
        });

        btnOverlay.setOnClickListener(v -> requestOverlay());''',"activity wiring")
    activity.write_text(src,encoding="utf-8")
    print("Browser bridge Activity UI, manifest, and Android permissions patched.")

if __name__=="__main__":
    main()
