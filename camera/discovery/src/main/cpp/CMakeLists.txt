cmake_minimum_required(VERSION 3.22.1)
project(camera_aux_discovery LANGUAGES CXX)

add_library(camera_aux_discovery SHARED native_aux_scan.cpp)

target_compile_features(camera_aux_discovery PRIVATE cxx_std_20)
target_compile_options(camera_aux_discovery PRIVATE -O2 -Wall -Wextra)

find_library(camera2ndk-lib camera2ndk)
find_library(log-lib log)

target_link_libraries(
    camera_aux_discovery
    ${camera2ndk-lib}
    ${log-lib}
)
