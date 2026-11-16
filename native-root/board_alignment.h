#pragma once
#include <algorithm>
#include <cmath>

// Browser DOM rectangles are in CSS viewport pixels, while SurfaceComposer
// positions are in physical display pixels. Keep all Y corrections together.
namespace chessroot {

struct BoardPlacement {
    int left;
    int top;
    float physicalWidth;
    float physicalSquare;
    float surfaceScale;
};

inline BoardPlacement placeBoard(int displayWidth, int displayHeight,
                                 float viewportWidth, float boardLeft,
                                 float boardTop, float boardWidth,
                                 int fixedOffsetY, float squareOffsetY) {
    const float ratio = static_cast<float>(displayWidth) / viewportWidth;
    const float width = std::clamp(boardWidth * ratio, 100.f,
                                   static_cast<float>(displayWidth));
    const float physicalSquare = width / 8.f;

    // A square-relative offset remains correct at any browser zoom or phone
    // resolution. Positive values shift the overlay DOWN the screen.
    const float correctedTop = boardTop * ratio + fixedOffsetY
                               + squareOffsetY * physicalSquare;
    const int left = std::clamp(static_cast<int>(std::lround(boardLeft * ratio)),
                                0, std::max(0, displayWidth - 80));
    const int top = std::clamp(static_cast<int>(std::lround(correctedTop)),
                               0, std::max(0, displayHeight - 80));
    return {left, top, width, physicalSquare, width / 1024.f};
}

} // namespace chessroot
