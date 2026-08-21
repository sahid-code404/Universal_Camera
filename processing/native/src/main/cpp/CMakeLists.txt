cmake_minimum_required(VERSION 3.22.1)
project(camera_processing LANGUAGES CXX)

add_library(camera_processing SHARED native_bridge.cpp)
target_compile_features(camera_processing PRIVATE cxx_std_20)
target_compile_options(camera_processing PRIVATE -O3 -ffast-math -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(camera_processing PRIVATE ${log-lib})
