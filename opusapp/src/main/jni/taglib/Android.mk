  LOCAL_PATH := $(call my-dir)

  include $(CLEAR_VARS)

  LOCAL_MODULE := taglib

  LOCAL_SRC_FILES := $(LOCAL_PATH)/../3rdparty/libs/$(TARGET_ARCH_ABI)/libtaglib.so
  LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../3rdparty/jni/taglib/include
  LOCAL_EXPORT_LDLIBS := $(LOCAL_PATH)/../3rdparty/libs/$(TARGET_ARCH_ABI)/libtaglib.so

  include $(PREBUILT_SHARED_LIBRARY)
