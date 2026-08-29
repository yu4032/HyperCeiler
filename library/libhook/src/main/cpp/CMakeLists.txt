cmake_minimum_required(VERSION 3.22)
project(hyperceiler_launcher LANGUAGES CXX ASM)

add_library(
    hyperceiler_launcher
    SHARED
    launcher_icon_title.cpp
    launcher_title_size_arm64.S
)

target_compile_features(hyperceiler_launcher PRIVATE cxx_std_17)
target_compile_options(hyperceiler_launcher PRIVATE -Wall -Wextra -Werror)
target_link_libraries(hyperceiler_launcher PRIVATE log dl)
