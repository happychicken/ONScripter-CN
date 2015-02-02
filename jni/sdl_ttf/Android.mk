LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := sdl_ttfn

LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/../sdl-1.2_jni/include $(LOCAL_PATH)/../freetype/include

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%)

LOCAL_CPP_EXTENSION := .cpp

# Note this simple makefile var substitution, you can find even simpler examples in different Android projects
LOCAL_SRC_FILES := SDL_ttf.c

LOCAL_SHARED_LIBRARIES := sdl-1.2_jni
LOCAL_STATIC_LIBRARIES := freetype
LOCAL_LDLIBS := -lz

include $(BUILD_SHARED_LIBRARY)

