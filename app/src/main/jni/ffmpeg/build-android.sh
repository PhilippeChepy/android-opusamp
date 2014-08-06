#!/bin/bash

#export ANDROID_NDK="/opt/android-ndk-r10"
#export ANDROID_PLATFORM=android-L
#export ANDROID_NDK_TOOLCHAIN_VERSION=4.9

export ANDROID_NDK="/opt/android-ndk-r9d"
export ANDROID_NDK_TOOLCHAIN_VERSION=4.8

export ANDROID_NDK_ARM_32_PLATFORM=android-8
export ANDROID_NDK_X86_32_PLATFORM=android-9
export ANDROID_NDK_MIPS_32_PLATFORM=android-9
export ANDROID_NDK_ARM_64_PLATFORM=android-L
export ANDROID_NDK_X86_64_PLATFORM=android-L
export ANDROID_NDK_MIPS_64_PLATFORM=android-L

if [ -z "$ANDROID_NDK" ]; then
    echo "You must define ANDROID_NDK before starting."
    echo "They must point to your NDK directories.\n"
    exit 1
fi

# Detect OS
OS=`uname`
HOST_ARCH=`uname -m`
export CCACHE=; type ccache >/dev/null 2>&1 && export CCACHE=ccache
if [ $OS == 'Linux' ]; then
    export HOST_SYSTEM=linux-x86_64 #$HOST_ARCH
elif [ $OS == 'Darwin' ]; then
    export HOST_SYSTEM=darwin-$HOST_ARCH
fi

platform="$1"

function arm_toolchain() {
    export CROSS_PREFIX=arm-linux-androideabi-
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_ARM_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN
}

function x86_toolchain() {
    export CROSS_PREFIX=i686-linux-android-
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=x86-${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_X86_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN
}

function mips_toolchain() {
    export CROSS_PREFIX=mipsel-linux-android-
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_MIPS_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN
}

function arm64_toolchain() {
    export CROSS_PREFIX=aarch64-linux-android-
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_ARM_64_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN
}

function x86_64_toolchain() {
    export CROSS_PREFIX=x86_64-linux-android-
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_X86_64_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN
}

function mips64_toolchain() {
    export CROSS_PREFIX=mips64el-linux-android-
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_MIPS_64_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN
}

SOURCE=`pwd`
DEST=$SOURCE/build/

TOOLCHAIN=/tmp/android-cross-compilers
SYSROOT=$TOOLCHAIN/sysroot/

rm $TOOLCHAIN -rf

if [ "$platform" = "x86" ]; then
    echo "Building x86 ffmpeg..."
    x86_toolchain
    TARGET="x86"
elif [ "$platform" = "mips" ]; then
    echo "Building mips ffmpeg..."
    mips_toolchain
    TARGET="mips"
elif [ "$platform" = "arm" ]; then
    echo "Building arm ffmpeg..."
    arm_toolchain
    TARGET="armv6 armv7"
elif [ "$platform" = "x86_64" ]; then
    echo "Building x86_64 ffmpeg..."
    x86_64_toolchain
    TARGET="x86_64"
elif [ "$platform" = "mips64" ]; then
    echo "Building mips64 ffmpeg..."
    mips64_toolchain
    TARGET="mips64"
elif [ "$platform" = "arm64" ]; then
    echo "Building arm64 ffmpeg..."
    arm64_toolchain
    TARGET="armv8"
fi

export PATH=$TOOLCHAIN/bin:$PATH
export CC="$CCACHE ${CROSS_PREFIX}gcc"
export CXX=${CROSS_PREFIX}g++
export LD=${CROSS_PREFIX}ld
export AR=${CROSS_PREFIX}ar
export STRIP=${CROSS_PREFIX}strip

CFLAGS="-std=c99 -O3 -w -pipe -fpic -fasm -finline-limit=300 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -fdiagnostics-color=always -DANDROID -DNDEBUG"
LDFLAGS="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack"

case $CROSS_PREFIX in
    aarch64-*)
        CFLAGS="-DANDROID"
        ;;
    arm-*)
        CFLAGS="-mthumb $CFLAGS -D__ARM_ARCH_5__ -D__ARM_ARCH_5E__ -D__ARM_ARCH_5T__ -D__ARM_ARCH_5TE__ -DANDROID"
        ;;
    x86_64-*)
        CFLAGS="-DANDROID"
        ;;
    x86-*)
        CFLAGS="-DANDROID"
        ;;
    mips64el-*)
        CFLAGS="-DANDROID"
        ;;
    mipsel-*)
        CFLAGS="-std=c99 -O3 -Wall -pipe -fpic -fasm  -ftree-vectorize -ffunction-sections -funwind-tables -fomit-frame-pointer -funswitch-loops \
        -finline-limit=300 -finline-functions -fpredictive-commoning -fgcse-after-reload -fipa-cp-clone -Wno-psabi -Wa,--noexecstack -DANDROID -DNDEBUG"
        ;;
esac

FFMPEG_FLAGS_COMMON="--target-os=linux \
--enable-cross-compile \
--cross-prefix=$CROSS_PREFIX \
--disable-gpl \
--enable-optimizations \
--enable-pic \
--enable-static \
--disable-shared \
--disable-asm \
--disable-debug \
--disable-ffplay \
--disable-ffprobe \
--disable-ffserver \
--disable-avdevice \
--disable-swscale \
--enable-swresample \
--enable-avfilter \
--disable-network \
--disable-decoders \
--enable-decoder=mp2 \
--enable-decoder=mp3 \
--enable-decoder=alac \
--enable-decoder=pcm_s16be \
--enable-decoder=pcm_s16le \
--enable-decoder=pcm_u16be \
--enable-decoder=pcm_u16le \
--enable-decoder=pcm_alaw \
--enable-decoder=pcm_mulaw \
--enable-decoder=pcm_s16le_planar \
--enable-decoder=adpcm_ms \
--enable-decoder=adpcm_g726 \
--enable-decoder=gsm \
--enable-decoder=gsm_ms \
--enable-decoder=tta \
--enable-decoder=ape \
--enable-decoder=flac \
--enable-decoder=vorbis \
--enable-decoder=wmav1 \
--enable-decoder=wmav2 \
--enable-decoder=wmalossless \
--enable-decoder=wmapro \
--enable-decoder=wmavoice \
--enable-decoder=aac \
--disable-protocols \
--enable-protocol=file \
--enable-protocol=pipe \
--disable-encoders \
--disable-muxers \
--disable-indevs \
--disable-outdevs \
--disable-bsfs \
--disable-demuxers \
--enable-demuxer=aac \
--enable-demuxer=mp3 \
--enable-demuxer=mov \
--enable-demuxer=asf \
--enable-demuxer=ogg \
--enable-demuxer=flac \
--enable-demuxer=pcm_s16be \
--enable-demuxer=pcm_s16le \
--enable-demuxer=pcm_u16be \
--enable-demuxer=pcm_u16le \
--enable-demuxer=pcm_alaw \
--enable-demuxer=pcm_mulaw \
--enable-demuxer=tta \
--enable-demuxer=wav \
--enable-demuxer=ape \
--enable-demuxer=xwma \
--disable-parsers \
--enable-parser=mpegaudio \
--enable-parser=aac \
--enable-filter=aresample \
--enable-filter=aconvert \
--enable-zlib"

for version in $TARGET; do
    cd $SOURCE
    FFMPEG_FLAGS="$FFMPEG_FLAGS_COMMON"

    case $version in
    armv8)
        FFMPEG_FLAGS="--arch=aarch64 --disable-runtime-cpudetect $FFMPEG_FLAGS --enable-asm"
        EXTRA_CFLAGS="-mcpu=generic -mtune=generic -march=armv8-a"
        EXTRA_LDFLAGS=""
        targetdir="arm64-v8a"
        ;;
    armv7)
        FFMPEG_FLAGS="--arch=arm --disable-runtime-cpudetect $FFMPEG_FLAGS --enable-asm"
        EXTRA_CFLAGS="-mfloat-abi=softfp -mfpu=vfpv3-d16 -marm -march=armv7-a"
        EXTRA_LDFLAGS=""
        targetdir="armeabi-v7a"
        ;;
    armv6)
        FFMPEG_FLAGS="--arch=arm --disable-runtime-cpudetect $FFMPEG_FLAGS --enable-asm"
        EXTRA_CFLAGS="-march=armv5"
        EXTRA_LDFLAGS=""
        targetdir="armeabi"
        ;;
    x86_64)
        FFMPEG_FLAGS="--arch=x86 --cpu=x86_64 --enable-runtime-cpudetect --enable-yasm --disable-amd3dnow --disable-amd3dnowext $FFMPEG_FLAGS"
        EXTRA_CFLAGS="-march=x86-64 -msse4.2 -mpopcnt -m64 -mtune=intel"
        EXTRA_LDFLAGS=""
        targetdir="x86_64"
        ;;
    x86)
        FFMPEG_FLAGS="--arch=x86 --cpu=i686 --enable-runtime-cpudetect --enable-yasm --disable-amd3dnow --disable-amd3dnowext $FFMPEG_FLAGS"
        EXTRA_CFLAGS="-march=i686 -mtune=intel -mstackrealign -mssse3 -mfpmath=sse -m32"
        EXTRA_LDFLAGS=""
        targetdir="x86"
        ;;
    mips64)
        FFMPEG_FLAGS="--arch=mips --cpu=mips64r6 --enable-runtime-cpudetect $FFMPEG_FLAGS"
        EXTRA_CFLAGS=""
        EXTRA_LDFLAGS=""
        targetdir="mips64"
        ;;
    mips)
        FFMPEG_FLAGS="--arch=mips --cpu=mips32r2 --enable-runtime-cpudetect --enable-yasm --disable-mipsfpu --disable-mipsdspr1 --disable-mipsdspr2 $FFMPEG_FLAGS --enable-asm"
        EXTRA_CFLAGS="-fno-strict-aliasing -fmessage-length=0 -fno-inline-functions-called-once -frerun-cse-after-loop -frename-registers"
        EXTRA_LDFLAGS=""
        targetdir="mips"
        ;;
    *)
        FFMPEG_FLAGS=""
        EXTRA_CFLAGS=""
        EXTRA_LDFLAGS=""
        targetdir=""
        ;;
    esac

    PREFIX="$DEST$targetdir" && rm -rf $PREFIX && mkdir -p $PREFIX
    FFMPEG_FLAGS="$FFMPEG_FLAGS --prefix=$PREFIX"

    ./configure $FFMPEG_FLAGS --extra-cflags="$CFLAGS $EXTRA_CFLAGS" --extra-ldflags="$LDFLAGS $EXTRA_LDFLAGS" | tee $PREFIX/configuration.txt
    cp config.* $PREFIX
    [ $PIPESTATUS == 0 ] || exit 1

    make clean
    find . -name "*.o" -type f -delete
    make -j5 install || exit 1

    case $CROSS_PREFIX in
      aarch64-*)
        $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o libavutil/aarch64/*.o
        ;;
      arm-*)
        $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o libavutil/arm/*.o  libavcodec/arm/*.o libswresample/arm/*.o
        ;;
      i686-*)
        $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o
        ;;
      x86_64-*)
        $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o
        ;;
      mipsel-*)
        $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o libavutil/mips/*.o  libavcodec/mips/*.o
        ;;
      mips64el-*)
        $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o
        ;;
    esac

    ${STRIP} --strip-unneeded $PREFIX/libffmpeg.so
    echo "Done : $version."
done
