#
# Android.mk
#
# Copyright (c) 2012, Philippe Chepy
# All rights reserved.
#
# This software is the confidential and proprietary information
# of Philippe Chepy.
# You shall not disclose such Confidential Information.
#
# http://www.chepy.eu
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := medialib

SRCS :=  audio_engine/outputs/safetrack.c \
	audio_engine/inputs/ffinput.c \
	audio_engine/utils/memory.c \
	audio_engine/utils/circular_buffer.c \
	audio_engine/processor/equalizer.c \
	audio_engine/engine.c \
	Base64.cpp \
	JniCodec.c \
	TagProvider.cpp

SRCS +=	audio_engine/processor/fft-ooura/fft.c

### Future implementation using simd code.
#SRCS +=	audio_engine/processor/fft/rfft.c
#SRCS +=	audio_engine/processor/fft/dft.c
#SRCS +=	audio_engine/processor/fft/simd_base.c
#SRCS +=	audio_engine/processor/fft/impl_pure_c.c

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#    LOCAL_CFLAGS += -mfloat-abi=softfp -mfpu=neon
#    LOCAL_CFLAGS += -DENABLE_NEON_FLOAT
#    SRCS += audio_engine/processor/fft/impl_armv7.c
endif # TARGET_ARCH_ABI == armeabi-v7a

ifeq ($(TARGET_ARCH_ABI),x86)
#    LOCAL_CFLAGS += -DENABLE_SSE_FLOAT
#    SRCS += audio_engine/processor/fft/impl_x86_sse.c

#     -DENABLE_AVX_FLOAT
#    SRCS += audio_engine/processor/fft/impl_x86_avx.c
endif # TARGET_ARCH_ABI == x86


LOCAL_SRC_FILES := $(SRCS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/audio_engine/effects/utils/nsfft/simd/
LOCAL_C_INCLUDES += $(LOCAL_PATH)/audio_engine/effects/utils/nsfft/dft/



LOCAL_STATIC_LIBRARIES = libffmpeg libtaglib

LOCAL_ALLOW_UNDEFINED_SYMBOLS := false

LOCAL_CFLAGS += -Wall -DHAVE_ZLIB -D__ANDROID__ -D__STDC_CONSTANT_MACROS
LOCAL_CFLAGS += -DTAGLIB_NO_CONFIG -DHAVE_ZLIB -DTAGLIB_WITH_MP4 -DWITH_MP4 -D__ANDROID__ -DTAGLIB_WITH_ASF -DWITH_ASF
LOCAL_CFLAGS += -DENABLE_PUREC_FLOAT

LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS := -L$(LOCAL_PATH)/../../libs/$(TARGET_ARCH_ABI)


include $(BUILD_SHARED_LIBRARY)