# Chess Root ImGui — standalone Android ARM64 executable

This version is a native **C++ ELF** for rooted Android 14–16 devices, not an APK. It uses **Dear ImGui, OpenGL ES 3 and a private SurfaceComposerClient layer**. It does not need MediaProjection.

It listens on **127.0.0.1:8765** for authenticated board positions and hints from the existing *Chess Trainer Browser Bridge* extension. It does not read the browser DOM itself; the extension reads the DOM and runs Stockfish locally.

## Installation in Termux (KernelSU / root)

1. Download `chess-root-imgui-arm64` from this release and save it in Android Download.
2. Enable `termux-setup-storage` if Termux does not have storage permission.
3. Run:

```sh
su -c 'cp /sdcard/Download/chess-root-imgui-arm64 /data/local/tmp/chess-root-imgui-arm64 && chmod 755 /data/local/tmp/chess-root-imgui-arm64'
su -c '/data/local/tmp/chess-root-imgui-arm64 --demo'
```

The demo draws a protected neon e2→e4 sample overlay (the position is for testing only). Stop with Ctrl+C or `su -c 'pkill -f chess-root-imgui-arm64'`.

## Browser connection

Stop the demo and run:

```sh
su -c '/data/local/tmp/chess-root-imgui-arm64 --y-offset 140'
```

The process prints a **12-character pairing code**. Enter it into the extension's *Android-оверлей → Код связи* field, enable that mode and open a supported chess site.

If the old Chess External APK is still running its bridge, **stop its browser service first**, because both use TCP port **8765**.

To set your own code: `--token 012345abcdef`.

The `--y-offset` option adds a positive or negative number of **physical screen pixels** when the browser's toolbar causes the overlay to be vertically misaligned. The screen pixel scale comes from the viewport width sent by the extension.

The executable supports `--help` and `--port N` (default port 8765). No rooting modules, code injection into a browser, or Android app installation is necessary for this overlay itself.

## Screen recording limitation — important

The surface is created with `skipScreenshot=true` and is positioned only over the chessboard. **This is a private OEM-dependent Android API**, not a promise of invisibility.

On some Android builds the overlay disappears from the captured video; on others it creates a black patch, remains visible, or fails to create a layer. Neither root access nor Dear ImGui guarantees clean recording. This program does not bypass game anti-cheat systems or edit screen recorder apps.

The device must expose matching private `libgui.so` symbols; if it does not, the program may fail at startup. Back up your configuration and avoid changing SELinux or disabling device security for this program.

## Credits

SurfaceComposer runtime and bundled ImGui: YiJieqwq/Android-Native-Overlay (MIT), pinned commit `67d9745670053e223f73fc3b80d071e931040261`.

Use for offline chess training, bot matches and otherwise permitted analysis; third-party real-time assistance may break the rules of chess sites.
