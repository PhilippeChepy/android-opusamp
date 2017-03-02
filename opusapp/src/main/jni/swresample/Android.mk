  LOCAL_PATH := $(call my-dir)

  include $(CLEAR_VARS)

  LOCAL_MODULE := swresample

  LOCAL_STATIC_LIBRARIES = soxr

  LOCAL_SRC_FILES := $(LOCAL_PATH)/../3rdparty/jni/ffmpeg/build/$(TARGET_ARCH_ABI)/lib/libswresample.so
  LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../3rdparty/jni/ffmpeg/build/$(TARGET_ARCH_ABI)/include
  LOCAL_EXPORT_LDLIBS := $(LOCAL_PATH)/../3rdparty/jni/ffmpeg/build/$(TARGET_ARCH_ABI)/lib/libswresample.so

  include $(PREBUILT_SHARED_LIBRARY)
