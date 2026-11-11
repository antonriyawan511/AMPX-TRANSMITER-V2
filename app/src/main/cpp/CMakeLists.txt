cmake_minimum_required(VERSION 3.22.1)
project("native_opus")

set(OPUS_BUILD_TESTING OFF CACHE BOOL "build tests" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "build programs" FORCE)
set(OPUS_FIXED_POINT ON CACHE BOOL "compile fixed-point" FORCE)
set(OPUS_DISABLE_INTRINSICS ON CACHE BOOL "disable intrinsics" FORCE)

add_compile_options(-O3 -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0)

add_subdirectory(opus)

add_library(
    native_opus
    SHARED
    native_opus.c
)

target_include_directories(
    native_opus
    PRIVATE
    opus/include
)

target_link_libraries(
    native_opus
    opus
    log
)
