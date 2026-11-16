cmake_minimum_required(VERSION 3.22.1)
project(chessimgui LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(IMGUI_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/vendor/imgui")
if(NOT EXISTS "${IMGUI_ROOT}/imgui.cpp")
  message(FATAL_ERROR "Dear ImGui not found. Download pinned v1.93.0 in CI first.")
endif()

add_library(chessimgui SHARED
    NativeChessImGui.cpp
    "${IMGUI_ROOT}/imgui.cpp"
    "${IMGUI_ROOT}/imgui_draw.cpp"
    "${IMGUI_ROOT}/imgui_tables.cpp"
    "${IMGUI_ROOT}/imgui_widgets.cpp"
    "${IMGUI_ROOT}/backends/imgui_impl_opengl3.cpp")

target_include_directories(chessimgui PRIVATE
    "${IMGUI_ROOT}"
    "${IMGUI_ROOT}/backends")

target_compile_definitions(chessimgui PRIVATE IMGUI_IMPL_OPENGL_ES3=1)
target_compile_options(chessimgui PRIVATE -Wall -Wextra)
find_library(log_lib log)
find_library(gles_lib GLESv3)
find_library(egl_lib EGL)
target_link_libraries(chessimgui ${log_lib} ${gles_lib} ${egl_lib})
