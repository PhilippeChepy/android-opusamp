LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := soxr

LOCAL_SRC_FILES := src/data-io.c \
    src/dbesi0.c \
    src/filter.c \
    src/lsr.c \
    src/rate32.c \
    src/rate64.c \
    src/soxr.c \
    src/vr32.c \
    src/fft4g.c \
    src/fft4g64.c \
    src/fft4g32.c

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include $(LOCAL_PATH)/../ffmpeg/build/$(TARGET_ARCH_ABI)/include/

LOCAL_CFLAGS += -DSOXR_LIB -DSOXR_VISIBILITY -Dsoxr_lsr_EXPORTS
LOCAL_LDLIBS := -lz -llog
LOCAL_LDFLAGS := -L$(LOCAL_PATH)/../../libs/$(TARGET_ARCH_ABI)

include $(BUILD_SHARED_LIBRARY)