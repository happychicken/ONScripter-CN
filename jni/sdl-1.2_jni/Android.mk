LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

#LOCAL_ARM_MODE := arm

LOCAL_MODULE := $(lastword $(subst /, ,$(LOCAL_PATH)))

ifndef SDL_JAVA_PACKAGE_PATH
$(error Please define SDL_JAVA_PACKAGE_PATH to the path of your Java package with dots replaced with underscores, for example "com_example_SanAngeles")
endif

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS := -O3 -DSDL_JAVA_PACKAGE_PATH=$(SDL_JAVA_PACKAGE_PATH) -DSDL_VIDEO_RENDER_RESIZE=1 -DHAVE_INTTYPES_H -DSDL_HAS_64BIT_TYPE

#	-DSDL_TRACKBALL_KEYUP_DELAY=$(SDL_TRACKBALL_KEYUP_DELAY) \
#	-DSDL_VIDEO_RENDER_RESIZE_KEEP_ASPECT=$(SDL_VIDEO_RENDER_RESIZE_KEEP_ASPECT) \
#	-DSDL_VIDEO_RENDER_RESIZE=$(SDL_VIDEO_RENDER_RESIZE) \
#	$(SDL_ADDITIONAL_CFLAGS)


SDL_SRCS := \
	src/*.c \
	src/audio/*.c \
	src/cdrom/*.c \
	src/cpuinfo/*.c \
	src/events/*.c \
	src/file/*.c \
	src/haptic/*.c \
	src/joystick/*.c \
	src/stdlib/*.c \
	src/thread/*.c \
	src/timer/*.c \
	src/video/*.c \
	src/main/*.c \
	src/power/*.c \
	src/thread/pthread/*.c \
	src/timer/unix/*.c \
	src/audio/android/*.c \
	src/cdrom/dummy/*.c \
	src/video/android/*.c \
	src/video/android/*.cpp \
	src/joystick/dummy/*.c \
	src/haptic/dummy/*.c \
	src/loadso/dlopen/*.c \
	src/atomic/dummy/*.c 

# TODO: use libcutils for atomic operations, but it's not included in NDK

#	src/atomic/linux/*.c \
#	src/power/linux/*.c \
#	src/joystick/android/*.c \
#	src/haptic/android/*.c \
#	src/libm/*.c \

LOCAL_CPP_EXTENSION := .cpp

# Note this "simple" makefile var substitution, you can find even more complex examples in different Android projects
LOCAL_SRC_FILES := $(foreach F, $(SDL_SRCS), $(addprefix $(dir $(F)),$(notdir $(wildcard $(LOCAL_PATH)/$(F)))))

LOCAL_LDLIBS := -lGLESv1_CM -ldl -llog

include $(BUILD_SHARED_LIBRARY)
