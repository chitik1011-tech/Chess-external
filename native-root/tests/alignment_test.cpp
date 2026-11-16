#include "../board_alignment.h"
#include <cassert>
#include <cmath>
#include <cstdio>

int main() {
    using chessroot::placeBoard;
    // Browser uses CSS pixels: 393 CSS px = 1080 physical px.
    auto baseline = placeBoard(1080,2400,393.f,0,180,393,140,0.f);
    auto corrected = placeBoard(1080,2400,393.f,0,180,393,140,2.f);
    assert(baseline.physicalWidth == 1080.f);
    assert(baseline.physicalSquare == 135.f);
    assert(corrected.top - baseline.top == 270);
    assert(corrected.left == baseline.left);
    // Default correction tracks scaled boards, not a fixed CSS-pixel guess.
    auto halfBase = placeBoard(1080,2400,393.f,20,180,196.5f,140,0.f);
    auto halfFix = placeBoard(1080,2400,393.f,20,180,196.5f,140,2.f);
    assert(std::abs((halfFix.top - halfBase.top) - 135) <= 1);
    // An extra pixel nudge remains independent and additive.
    auto nudge = placeBoard(1080,2400,393.f,0,180,393,160,2.f);
    assert(nudge.top - corrected.top == 20);
    // Set zero to restore v0.4.1 coordinates.
    auto revert = placeBoard(1080,2400,393.f,0,180,393,140,0.f);
    assert(revert.top == baseline.top);
    // Small/large corrections are clamped within the physical screen.
    auto clampTop = placeBoard(1080,2400,393.f,0,-1000,393,0,-2.f);
    assert(clampTop.top == 0);
    std::puts("Board alignment OK: +2 squares, responsive sizing, offset, reset and clamping");
}
