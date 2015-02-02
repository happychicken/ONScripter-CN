LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := sdl_imagen

LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/../jpeg/include $(LOCAL_PATH)/../png/include $(LOCAL_PATH)/../sdl-1.2_jni/include $(LOCAL_PATH)/include
LOCAL_CFLAGS := -O3 \
       -DLOAD_JPG -DLOAD_PNG -DLOAD_BMP -DLOAD_GIF -DLOAD_LBM \
       -DLOAD_PCX -DLOAD_PNM -DLOAD_TGA -DLOAD_XCF -DLOAD_XPM \
       -DLOAD_XV

LOCAL_CPP_EXTENSION := .cpp

LOCAL_SRC_FILES := $(notdir $(wildcard $(LOCAL_PATH)/*.c))

LOCAL_STATIC_LIBRARIES := png jpeg

LOCAL_SHARED_LIBRARIES := sdl-1.2_jni

LOCAL_LDLIBS := -lz

include $(BUILD_SHARED_LIBRARY)

