#!/usr/bin/env python3
"""Permit touch passthrough for our noninteractive SurfaceComposer/ImGui layer.

This modifies the pinned native overlay template, NOT Android global settings.
A normal native SurfaceControl may cause Android 12+ InputDispatcher to reject
touches to apps beneath it due to untrusted occlusion, even when the overlay
has no input channel. Marking it as a trusted overlay is a *privileged*
SurfaceFlinger operation; fail closed when unavailable.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
header=root/"template/include/native_surface/ANativeWindowCreator.h"
main=root/"examples/minimal_overlay/src/main.cpp"

def replace_once(text,old,new,label):
    n=text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 match; found {n}")
    return text.replace(old,new,1)

def main_patch():
    s=header.read_text(encoding="utf-8")
    anchor='''        static bool SetPosition(ANativeWindow *nativeWindow, float x, float y) {'''
    method='''        // This layer has no input channel and contains only rendered
        // hints. Android 12+ otherwise rejects touches to the underlying
        // browser as an *untrusted occlusion*. Mark it trusted without
        // globally disabling input protections or injecting touches.
        // This requires privileged SurfaceFlinger access; return false
        // if the ROM does not expose/allow the operation.
        static bool EnableTouchPassthrough(ANativeWindow *nativeWindow) {
            auto it = m_cachedSurfaceControl.find(nativeWindow);
            if (it == m_cachedSurfaceControl.end()) return false;
            auto &fn = detail::Functionals::GetInstance();
            if (!fn.SurfaceComposerClient__Transaction__SetTrustedOverlay ||
                !fn.SurfaceComposerClient__Transaction__Apply) return false;
            detail::StrongPointer<void> control{};
            control.pointer = it->second.data;
            detail::SurfaceComposerClientTransaction transaction;
            if (!transaction.SetTrustedOverlay(control, true)) return false;
            return 0 == transaction.Apply(true, false);
        }

        static bool SetPosition(ANativeWindow *nativeWindow, float x, float y) {'''
    s=replace_once(s,anchor,method,"surface wrapper passthrough")
    header.write_text(s,encoding="utf-8")

    s=main.read_text(encoding="utf-8")
    old=''' if(!surface){std::fprintf(stderr,"Secure layer creation failed\\n");return 7;}
 if(!gfx->Init_Render(surface,SIZE,SIZE)){std::fprintf(stderr,"EGL init failed\\n");return 8;}'''
    new=''' if(!surface){std::fprintf(stderr,"Secure layer creation failed\\n");return 7;}
 // The window is output-only: all touches must go to the chess browser.
 // Do not run the overlay if ColorOS rejects trusted passthrough, as an
 // untrusted layer may make the underlying app impossible to operate.
 if (!android::ANativeWindowCreator::EnableTouchPassthrough(surface)) {
  std::fprintf(stderr,
      "Touch passthrough unavailable: trusted overlay denied by SurfaceFlinger.\\n"
      "Cannot safely display this root overlay on this ROM; exiting.\\n");
  android::ANativeWindowCreator::Destroy(surface);
  return 9;
 }
 std::puts("Touch passthrough configured: overlay does not take taps.");
 std::fflush(stdout);
 if(!gfx->Init_Render(surface,SIZE,SIZE)){std::fprintf(stderr,"EGL init failed\\n");return 8;}'''
    s=replace_once(s,old,new,"root native overlay creation")
    main.write_text(s,encoding="utf-8")
    print("Touch-safe native SurfaceComposer layer patched; global protections unchanged.")

if __name__=="__main__":
    main_patch()
