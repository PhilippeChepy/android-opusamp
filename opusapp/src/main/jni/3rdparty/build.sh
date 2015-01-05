#!/bin/sh
PATH=/Users/phil/Library/Android/android-ndk-r10c:$PATH

cd $(dirname $0)

rm -rf jni > /dev/null
rm -rf libs > /dev/null
rm -rf obj > /dev/null

mkdir -p jni/ffmpeg
mkdir -p jni/soxr
mkdir -p jni/taglib
tar x -C jni -s /ffmpeg-2.4.3/ffmpeg/ -f src/ffmpeg-2.4.3.tar.bz2
tar x -C jni -s /soxr-0.1.1-Source/soxr/ -f src/soxr-0.1.1-Source.tar.xz
tar x -C jni -s /taglib-1.9.1/taglib/ -f src/taglib-1.9.1.tar.gz

#############################################################################################################
# Preparing build scripts for FFMpeg
#############################################################################################################
cp src/ffmpeg-build-android.sh jni/ffmpeg/build-android.sh
chmod +x jni/ffmpeg/build-android.sh

#############################################################################################################
# Preparing build scripts for SoXR
#############################################################################################################
cat > jni/soxr/Android.mk << "EOF"
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
EOF

cat > jni/soxr/soxr-config.h << "EOF"
/* SoX Resampler Library      Copyright (c) 2007-13 robs@users.sourceforge.net
 * Licence for this file: LGPL v2.1                  See LICENCE for details. */

#if !defined soxr_config_included
#define soxr_config_included

#define HAVE_SINGLE_PRECISION 1
#define HAVE_DOUBLE_PRECISION 1
#define HAVE_AVFFT            0
#define HAVE_SIMD             0
#define HAVE_FENV_H           0
#define HAVE_LRINT            1
#define WORDS_BIGENDIAN       0

#include <limits.h>

#undef bool
#undef false
#undef true
#define bool int
#define false 0
#define true 1

#undef int16_t
#undef int32_t
#undef int64_t
#undef uint32_t
#undef uint64_t
#define int16_t short
#if LONG_MAX > 2147483647L
  #define int32_t int
  #define int64_t long
#elif LONG_MAX < 2147483647L
#error this library requires that 'long int' has at least 32-bits
#else
  #define int32_t long
  #if defined _MSC_VER
    #define int64_t __int64
  #else
    #define int64_t long long
  #endif
#endif
#define uint32_t unsigned int32_t
#define uint64_t unsigned int64_t

#endif
EOF

#############################################################################################################
# Preparing build scripts for TagLib
#############################################################################################################
cat > jni/taglib/Android.mk << "EOF"
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := taglib

LOCAL_SRC_FILES := taglib/trueaudio/trueaudioproperties.cpp \
taglib/trueaudio/trueaudiofile.cpp \
taglib/tag.cpp \
taglib/mp4/mp4item.cpp \
taglib/mp4/mp4properties.cpp \
taglib/mp4/mp4atom.cpp \
taglib/mp4/mp4file.cpp \
taglib/mp4/mp4coverart.cpp \
taglib/mp4/mp4tag.cpp \
taglib/tagunion.cpp \
taglib/it/itfile.cpp \
taglib/it/itproperties.cpp \
taglib/riff/wav/wavfile.cpp \
taglib/riff/wav/wavproperties.cpp \
taglib/riff/wav/infotag.cpp \
taglib/riff/rifffile.cpp \
taglib/riff/aiff/aifffile.cpp \
taglib/riff/aiff/aiffproperties.cpp \
taglib/asf/asfpicture.cpp \
taglib/asf/asffile.cpp \
taglib/asf/asfproperties.cpp \
taglib/asf/asfattribute.cpp \
taglib/asf/asftag.cpp \
taglib/mod/modproperties.cpp \
taglib/mod/modtag.cpp \
taglib/mod/modfile.cpp \
taglib/mod/modfilebase.cpp \
taglib/wavpack/wavpackfile.cpp \
taglib/wavpack/wavpackproperties.cpp \
taglib/toolkit/tbytevectorstream.cpp \
taglib/toolkit/tpropertymap.cpp \
taglib/toolkit/tdebug.cpp \
taglib/toolkit/tdebuglistener.cpp \
taglib/toolkit/tbytevectorlist.cpp \
taglib/toolkit/trefcounter.cpp \
taglib/toolkit/tstring.cpp \
taglib/toolkit/tstringlist.cpp \
taglib/toolkit/tbytevector.cpp \
taglib/toolkit/tfilestream.cpp \
taglib/toolkit/tiostream.cpp \
taglib/toolkit/unicode.cpp \
taglib/toolkit/tfile.cpp \
taglib/mpc/mpcfile.cpp \
taglib/mpc/mpcproperties.cpp \
taglib/audioproperties.cpp \
taglib/ape/apeitem.cpp \
taglib/ape/apefooter.cpp \
taglib/ape/apetag.cpp \
taglib/ape/apefile.cpp \
taglib/ape/apeproperties.cpp \
taglib/ogg/xiphcomment.cpp \
taglib/ogg/vorbis/vorbisfile.cpp \
taglib/ogg/vorbis/vorbisproperties.cpp \
taglib/ogg/speex/speexproperties.cpp \
taglib/ogg/speex/speexfile.cpp \
taglib/ogg/oggpage.cpp \
taglib/ogg/oggpageheader.cpp \
taglib/ogg/opus/opusfile.cpp \
taglib/ogg/opus/opusproperties.cpp \
taglib/ogg/oggfile.cpp \
taglib/ogg/flac/oggflacfile.cpp \
taglib/fileref.cpp \
taglib/s3m/s3mproperties.cpp \
taglib/s3m/s3mfile.cpp \
taglib/flac/flacmetadatablock.cpp \
taglib/flac/flacproperties.cpp \
taglib/flac/flacfile.cpp \
taglib/flac/flacunknownmetadatablock.cpp \
taglib/flac/flacpicture.cpp \
taglib/xm/xmfile.cpp \
taglib/xm/xmproperties.cpp \
taglib/mpeg/mpegproperties.cpp \
taglib/mpeg/mpegfile.cpp \
taglib/mpeg/id3v2/id3v2footer.cpp \
taglib/mpeg/id3v2/frames/textidentificationframe.cpp \
taglib/mpeg/id3v2/frames/commentsframe.cpp \
taglib/mpeg/id3v2/frames/popularimeterframe.cpp \
taglib/mpeg/id3v2/frames/relativevolumeframe.cpp \
taglib/mpeg/id3v2/frames/generalencapsulatedobjectframe.cpp \
taglib/mpeg/id3v2/frames/privateframe.cpp \
taglib/mpeg/id3v2/frames/ownershipframe.cpp \
taglib/mpeg/id3v2/frames/urllinkframe.cpp \
taglib/mpeg/id3v2/frames/unsynchronizedlyricsframe.cpp \
taglib/mpeg/id3v2/frames/attachedpictureframe.cpp \
taglib/mpeg/id3v2/frames/unknownframe.cpp \
taglib/mpeg/id3v2/frames/uniquefileidentifierframe.cpp \
taglib/mpeg/id3v2/id3v2header.cpp \
taglib/mpeg/id3v2/id3v2synchdata.cpp \
taglib/mpeg/id3v2/id3v2extendedheader.cpp \
taglib/mpeg/id3v2/id3v2framefactory.cpp \
taglib/mpeg/id3v2/id3v2tag.cpp \
taglib/mpeg/id3v2/id3v2frame.cpp \
taglib/mpeg/xingheader.cpp \
taglib/mpeg/id3v1/id3v1tag.cpp \
taglib/mpeg/id3v1/id3v1genres.cpp \
taglib/mpeg/mpegheader.cpp

	
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_CFLAGS += -DTAGLIB_NO_CONFIG -DHAVE_ZLIB -DTAGLIB_WITH_MP4 -DWITH_MP4 -D__ANDROID__ -DTAGLIB_WITH_ASF -DWITH_ASF
LOCAL_LDLIBS := -lz -llog

include $(BUILD_SHARED_LIBRARY)
EOF


mkdir jni/taglib/include
find jni/taglib/taglib -name \*.h -exec cp {} jni/taglib/include/ \;
find jni/taglib/taglib -name \*.tcc -exec cp {} jni/taglib/include/ \;
cat > jni/taglib/include/taglib_config.h << "EOF"
/* taglib_config.h. Generated by cmake from taglib_config.h.cmake */

#define   TAGLIB_WITH_ASF 1
#define   TAGLIB_WITH_MP4 1
EOF


#############################################################################################################
# First step
#############################################################################################################
cat > jni/Application.mk << "EOF"
APP_ABI := x86 armeabi armeabi-v7a mips

APP_MODULES := soxr ffmpeg taglib
APP_PLATFORM := android-8

APP_STL := stlport_static
APP_CPPFLAGS += -frtti 
EOF

PREPARE_PATH=`pwd`

cat > jni/Android.mk << EOF
PROJECT_JNI_PATH := \$(PROJECT_JNI_PATH)
include ${PREPARE_PATH}/jni/soxr/Android.mk
include ${PREPARE_PATH}/jni/taglib/Android.mk
EOF

cd jni
ndk-build

cd ffmpeg
./build-android.sh armeabi
./build-android.sh armeabi-v7a
./build-android.sh mips
./build-android.sh x86

cd ../..

function prepare_ffmpeg_prebuilt_lib {
  rm -rf $1/$2
  mkdir -p $1/$2

cat > $1/$2/Android.mk << EOF
  LOCAL_PATH := \$(call my-dir)

  include \$(CLEAR_VARS)

  LOCAL_MODULE := $2

  LOCAL_STATIC_LIBRARIES = soxr

  LOCAL_SRC_FILES := \$(LOCAL_PATH)/../3rdparty/jni/ffmpeg/build/\$(TARGET_ARCH_ABI)/lib/lib$2.so
  LOCAL_EXPORT_C_INCLUDES := \$(LOCAL_PATH)/../3rdparty/jni/ffmpeg/build/\$(TARGET_ARCH_ABI)/include
  LOCAL_EXPORT_LDLIBS := \$(LOCAL_PATH)/../3rdparty/jni/ffmpeg/build/\$(TARGET_ARCH_ABI)/lib/lib$2.so

  include \$(PREBUILT_SHARED_LIBRARY)
EOF
}

function prepare_soxr_prebuilt_lib {
  rm -rf $1/$2
  mkdir -p $1/$2

cat > $1/$2/Android.mk << EOF
  LOCAL_PATH := \$(call my-dir)

  include \$(CLEAR_VARS)

  LOCAL_MODULE := $2

  LOCAL_SRC_FILES := \$(LOCAL_PATH)/../3rdparty/libs/\$(TARGET_ARCH_ABI)/lib$2.so
  LOCAL_EXPORT_C_INCLUDES := \$(LOCAL_PATH)/../3rdparty/jni/soxr/src
  LOCAL_EXPORT_LDLIBS := \$(LOCAL_PATH)/../3rdparty/libs/\$(TARGET_ARCH_ABI)/lib$2.so

  include \$(PREBUILT_SHARED_LIBRARY)
EOF
}

function prepare_taglib_prebuilt_lib {
  rm -rf $1/$2
  mkdir -p $1/$2

cat > $1/$2/Android.mk << EOF
  LOCAL_PATH := \$(call my-dir)

  include \$(CLEAR_VARS)

  LOCAL_MODULE := $2

  LOCAL_SRC_FILES := \$(LOCAL_PATH)/../3rdparty/libs/\$(TARGET_ARCH_ABI)/lib$2.so
  LOCAL_EXPORT_C_INCLUDES := \$(LOCAL_PATH)/../3rdparty/jni/taglib/include
  LOCAL_EXPORT_LDLIBS := \$(LOCAL_PATH)/../3rdparty/libs/\$(TARGET_ARCH_ABI)/lib$2.so

  include \$(PREBUILT_SHARED_LIBRARY)
EOF
}

function prepare_android_mk {
cat > $1/Application.mk << EOF
APP_ABI := armeabi-v7a armeabi x86 mips

APP_MODULES := medialib
APP_PLATFORM := android-8

APP_STL := stlport_static
APP_CPPFLAGS += -frtti 
EOF

cat > $1/Android.mk << EOF
PROJECT_JNI_PATH := \$(call my-dir)

include $1/soxr/Android.mk
include $1/taglib/Android.mk
include $1/avcodec/Android.mk
include $1/avfilter/Android.mk
include $1/avformat/Android.mk
include $1/avutil/Android.mk
include $1/swresample/Android.mk
include $1/medialib/Android.mk
EOF

}

PARENT_DIR=`cd ..; pwd`

prepare_ffmpeg_prebuilt_lib ${PARENT_DIR} "avcodec"
prepare_ffmpeg_prebuilt_lib ${PARENT_DIR} "avfilter"
prepare_ffmpeg_prebuilt_lib ${PARENT_DIR} "avformat"
prepare_ffmpeg_prebuilt_lib ${PARENT_DIR} "avutil"
prepare_ffmpeg_prebuilt_lib ${PARENT_DIR} "swresample"

prepare_soxr_prebuilt_lib ${PARENT_DIR} "soxr"
prepare_taglib_prebuilt_lib ${PARENT_DIR} "taglib"

prepare_android_mk ${PARENT_DIR}