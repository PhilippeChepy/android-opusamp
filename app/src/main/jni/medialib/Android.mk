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
	audio_engine/effects/equalizer.c \
	audio_engine/engine.c \
	JniCodec.c \
	TagProvider.cpp

LOCAL_SRC_FILES := $(SRCS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)

LOCAL_STATIC_LIBRARIES = libffmpeg libtaglib

LOCAL_ALLOW_UNDEFINED_SYMBOLS := false

LOCAL_CFLAGS += -Wall -DHAVE_ZLIB -D__ANDROID__ -D__STDC_CONSTANT_MACROS
LOCAL_CFLAGS += -DTAGLIB_NO_CONFIG -DHAVE_ZLIB -DTAGLIB_WITH_MP4 -DWITH_MP4 -D__ANDROID__ -DTAGLIB_WITH_ASF -DWITH_ASF

LOCAL_LDLIBS := -llog
LOCAL_LDFLAGS := -L$(LOCAL_PATH)/../../libs/$(TARGET_ARCH_ABI)


include $(BUILD_SHARED_LIBRARY)