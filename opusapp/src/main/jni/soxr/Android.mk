  LOCAL_PATH := $(call my-dir)

  include $(CLEAR_VARS)

  LOCAL_MODULE := soxr

  LOCAL_SRC_FILES := $(LOCAL_PATH)/../3rdparty/libs/$(TARGET_ARCH_ABI)/libsoxr.so
  LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../3rdparty/jni/soxr/src
  LOCAL_EXPORT_LDLIBS := $(LOCAL_PATH)/../3rdparty/libs/$(TARGET_ARCH_ABI)/libsoxr.so

  include $(PREBUILT_SHARED_LIBRARY)
