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

### Chessboard vertical alignment (v0.4.2)

When the neon/arrow is drawn **two ranks above** the intended squares,
v0.4.2 shifts the native layer **down by two board squares by default**.
The correction is relative to the detected board width, so it works on
different screen resolutions and browser zoom levels.

```sh
su -c '/data/local/tmp/chess-root-imgui-arm64 --y-squares 2 --y-offset 140'
```

* `--y-squares 2` — exactly **2 board squares down** (default in v0.4.2).
* `--y-squares 1.5` — 1.5 squares down; negative values move up.
* `--y-squares 0` — restores v0.4.1's original alignment.
* `--y-offset 140` — add a separate **140 physical pixel** adjustment;
  zero by default. Do not double-apply the correction if it is already aligned.

The program prints its active offsets at startup. Restart it after changing
arguments.

The screen pixel scale comes from the viewport width sent by the extension.

The executable supports `--help` and `--port N` (default port 8765). No rooting modules, code injection into a browser, or Android app installation is necessary for this overlay itself.

## Three Stockfish moves (v0.4.3)

With Chess Trainer browser extension **v0.9.2 or newer**, enable the
**3 хода: идеальный / хороший / плохой** switch. The extension sends
`move`, `goodMove`, `badMove` and `threeMode` through the same local
authenticated HTTP bridge. Root Dear ImGui now renders **three** ranked
suggestions: green BEST, cyan GOOD, red WEAK, including both arrow and
neon-square modes. The best is always drawn last/on top.

The "bad" category is relative to the engine analysis; if no line loses
at least 1.5 pawns it is explicitly labelled the *weakest evaluated option*
in the extension panel. Stockfish's MultiPV 12 is not a full enumeration
of every legal move.

The bridge remains backward compatible with older extensions (one move).
For Android 15/ColorOS 15, passthrough and screenshot exclusion still
depend on private SurfaceComposer behavior; test on your device.

## Touch passthrough (v0.4.1)

Version 0.4.0 can block touches on Android 12–15 when the native layer
is considered an untrusted overlay. **Version 0.4.1 and newer request a trusted
SurfaceComposer layer**, allowing taps on the underlying chess board.

The ImGui drawing is display-only; it is NOT a touch target. If the ROM
rejects the privileged trusted-layer operation, the program exits with
`Touch passthrough unavailable` instead of showing a blocking overlay.

Do not globally disable Android's `block_untrusted_touches` security feature.

To replace an older running binary, first stop the old process in Termux,
then copy the downloaded v0.4.1 binary over /data/local/tmp and launch it
again. Old running processes keep the previous in-memory version until
restarted.

## Screen recording limitation — important

The surface is created with `skipScreenshot=true` and is positioned only over the chessboard. **This is a private OEM-dependent Android API**, not a promise of invisibility.

On some Android builds the overlay disappears from the captured video; on others it creates a black patch, remains visible, or fails to create a layer. Neither root access nor Dear ImGui guarantees clean recording. This program does not bypass game anti-cheat systems or edit screen recorder apps.

The device must expose matching private `libgui.so` symbols; if it does not, the program may fail at startup. Back up your configuration and avoid changing SELinux or disabling device security for this program.

## Credits

SurfaceComposer runtime and bundled ImGui: YiJieqwq/Android-Native-Overlay (MIT), pinned commit `67d9745670053e223f73fc3b80d071e931040261`.

Use for offline chess training, bot matches and otherwise permitted analysis; third-party real-time assistance may break the rules of chess sites.
