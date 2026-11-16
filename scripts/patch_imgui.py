#!/usr/bin/env python3
"""Switch the browser-only overlay to real Dear ImGui via EGL/GLES3+NDK.

Must be applied AFTER patch_browser_bridge.py. Fails if upstream drifts.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
bridge=root/"app/src/main/java/com/opencheps/bridge/BrowserBridgeService.java"
gradle=root/"app/build.gradle"

def one(s,old,new,label):
    n=s.count(old)
    if n != 1:raise ValueError(f"{label}: expected one match, found {n}")
    return s.replace(old,new,1)

def main():
    s=bridge.read_text(encoding="utf-8")
    s=one(s,
        "    private BrowserHintView hint;",
        "    private BrowserImGuiView hint;",
        "overlay view type")
    s=one(s,
        "            hint=new BrowserHintView(this);",
        """            if (!BrowserImGuiView.isLibraryAvailable()) {
                Log.e(TAG,"ImGui JNI not present");
                return;
            }
            hint=new BrowserImGuiView(this);""",
        "create ImGui overlay")
    s=one(s,
        "        hint.update(move,mode,quality,flipped,event,speed);",
        "        hint.update(move,mode,quality,flipped,event,speed);",
        "existing update endpoint")
    bridge.write_text(s,encoding="utf-8")

    g=gradle.read_text(encoding="utf-8")
    g=one(g,
        """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }""",
        """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    ndkVersion '27.2.12479018'

    externalNativeBuild {
        cmake {
            path 'src/main/cpp/CMakeLists.txt'
            version '3.22.1'
        }
    }""",
        "Gradle NDK and CMake registration")
    gradle.write_text(g,encoding="utf-8")
    print("Switched native browser bridge to Dear ImGui/OpenGL ES3")

if __name__=="__main__":
    main()
