#!/bin/bash

export ANDROID_NDK="/mnt/net/devel/tools/android-ndk"
export ANDROID_NDK_TOOLCHAIN_VERSION=4.8

export ANDROID_NDK_ARM_32_PLATFORM=android-8
export ANDROID_NDK_X86_32_PLATFORM=android-9
export ANDROID_NDK_MIPS_32_PLATFORM=android-9
export ANDROID_NDK_ARM_64_PLATFORM=android-21
export ANDROID_NDK_X86_64_PLATFORM=android-21
export ANDROID_NDK_MIPS_64_PLATFORM=android-21

export HOST_SYSTEM=linux-x86_64

platform="$1"

echo ">>> Building ffmpeg. "

SOURCE=`pwd`
TARGET_DIR=$SOURCE/build/
PREFIX="$TARGET_DIR$platform"

TOOLCHAIN=/tmp/android-cross-compilers
SYSROOT=$TOOLCHAIN/sysroot/

rm $TOOLCHAIN -rf

export PATH=$TOOLCHAIN/bin:$PATH

FFMPEG_FLAGS_COMMON="--disable-gpl \
--disable-programs \
--disable-doc \
--enable-optimizations \
--enable-pic \
--enable-libsoxr \
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

CFLAGS="-std=c99 -O3 -fomit-frame-pointer -w -pipe -fpic -fPIC -fasm -finline-limit=300 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -fdiagnostics-color=always -DNDEBUG -I../soxr/src/"
LDFLAGS="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack -L../../libs/$platform/ -lsoxr"

echo -n "     * Cleaning previous build. "
rm -rf $PREFIX
mkdir -p $PREFIX
echo "Done."

echo -n "     * Preparing environement for $platform arch. "

if [ "$platform" = "x86" ]; then
	export CROSS_PREFIX=i686-linux-android-

	export CFLAGS="$CFLAGS -DANDROID"
	export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=x86 --cpu=i686 --enable-runtime-cpudetect --enable-yasm --disable-amd3dnow --disable-amd3dnowext $FFMPEG_FLAGS_COMMON"
	export EXTRA_CFLAGS="-march=i686 -mtune=intel -mstackrealign -mssse3 -mfpmath=sse -m32"
	export EXTRA_LDFLAGS=""

	$ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=x86-${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_X86_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
elif [ "$platform" = "mips" ]; then
	export CROSS_PREFIX=mipsel-linux-android-

	export CFLAGS="$CFLAGS -ftree-vectorize -ffunction-sections -funwind-tables -funswitch-loops -finline-functions -fpredictive-commoning -fgcse-after-reload -fipa-cp-clone -DANDROID"
	export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=mips --cpu=mips32r2 --enable-runtime-cpudetect --enable-yasm --disable-mipsfpu --disable-mipsdspr1 --disable-mipsdspr2 $FFMPEG_FLAGS_COMMON --enable-asm"
	export EXTRA_CFLAGS="-fno-strict-aliasing -fmessage-length=0 -fno-inline-functions-called-once -frerun-cse-after-loop -frename-registers"
	export EXTRA_LDFLAGS=""

	$ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_MIPS_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
elif [ "$platform" = "armeabi" ]; then
    export CROSS_PREFIX=arm-linux-androideabi-

	export CFLAGS="-mthumb $CFLAGS -D__ARM_ARCH_5__ -D__ARM_ARCH_5E__ -D__ARM_ARCH_5T__ -D__ARM_ARCH_5TE__ -DANDROID"
    export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=arm --disable-runtime-cpudetect $FFMPEG_FLAGS_COMMON --enable-asm"
    export EXTRA_CFLAGS="-march=armv5"
    export EXTRA_LDFLAGS=""

    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_ARM_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
elif [ "$platform" = "armeabi-v7a" ]; then
    export CROSS_PREFIX=arm-linux-androideabi-

	export CFLAGS="-mthumb $CFLAGS -D__ARM_ARCH_5__ -D__ARM_ARCH_5E__ -D__ARM_ARCH_5T__ -D__ARM_ARCH_5TE__ -DANDROID"
    export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=arm --disable-runtime-cpudetect $FFMPEG_FLAGS_COMMON --enable-asm"
    export EXTRA_CFLAGS="-mfloat-abi=softfp -mfpu=vfpv3-d16 -marm -march=armv7-a"
    export EXTRA_LDFLAGS=""

    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_ARM_32_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
elif [ "$platform" = "x86_64" ]; then
    export CROSS_PREFIX=x86_64-linux-android-

	export CFLAGS="$CFLAGS -DANDROID"
    export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=x86 --cpu=x86_64 --enable-runtime-cpudetect --enable-yasm --disable-amd3dnow --disable-amd3dnowext $FFMPEG_FLAGS_COMMON"
    export EXTRA_CFLAGS="-march=x86-64 -msse4.2 -mpopcnt -m64 -mtune=intel"
    export EXTRA_LDFLAGS=""

    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_X86_64_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
elif [ "$platform" = "mips64el" ]; then
    export CROSS_PREFIX=mips64el-linux-android-

	export CFLAGS="$CFLAGS -DANDROID"
    export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=mips --cpu=mips64r6 --enable-runtime-cpudetect $FFMPEG_FLAGS_COMMON"
    export EXTRA_CFLAGS=""
    export EXTRA_LDFLAGS=""

    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_MIPS_64_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
elif [ "$platform" = "aarch64" ]; then
    export CROSS_PREFIX=aarch64-linux-android-

	export CFLAGS="$CFLAGS -DANDROID"
	export FFMPEG_FLAGS="--target-os=linux --enable-cross-compile --cross-prefix=$CROSS_PREFIX --arch=aarch64 --disable-runtime-cpudetect $FFMPEG_FLAGS_COMMON --enable-asm"
    export EXTRA_CFLAGS="-mcpu=generic -mtune=generic -march=armv8-a"
    export EXTRA_LDFLAGS=""

    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${CROSS_PREFIX}${ANDROID_NDK_TOOLCHAIN_VERSION} --platform=${ANDROID_NDK_ARM_64_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
fi
echo "Done."

export CC=${CROSS_PREFIX}gcc
export CXX=${CROSS_PREFIX}g++
export LD=${CROSS_PREFIX}ld
export AR=${CROSS_PREFIX}ar
export STRIP=${CROSS_PREFIX}strip

cd $SOURCE

FFMPEG_FLAGS="$FFMPEG_FLAGS --prefix=$PREFIX"

echo -n "     * Configuring build... "
./configure $FFMPEG_FLAGS --extra-cflags="$CFLAGS $EXTRA_CFLAGS" --extra-ldflags="$LDFLAGS $EXTRA_LDFLAGS" > $PREFIX/build-configure.log
cp config.* $PREFIX
echo "Done. "

[ $PIPESTATUS == 0 ] || exit 1

echo -n "     * Building library components... "
make clean > /dev/null
find . -name "*.o" -type f -delete
make -j5 install > $PREFIX/build-make.log 2> $PREFIX/build-make-errors.log || exit 1
echo "Done. "

echo -n "     * Linking library to make shared object... "
case $CROSS_PREFIX in
  aarch64-*)
    $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o libavutil/aarch64/*.o >> $PREFIX/build-make.log
    ;;
  arm-*)
    $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o libavutil/arm/*.o  libavcodec/arm/*.o libswresample/arm/*.o >> $PREFIX/build-make.log
    ;;
  i686-*)
    $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o >> $PREFIX/build-make.log
    ;;
  x86_64-*)
    $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o >> $PREFIX/build-make.log
    ;;
  mipsel-*)
    $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o libavutil/mips/*.o  libavcodec/mips/*.o >> $PREFIX/build-make.log
    ;;
  mips64el-*)
    $CC -o $PREFIX/libffmpeg.so -shared $LDFLAGS $EXTRA_LDFLAGS libavutil/*.o libavcodec/*.o libavformat/*.o libavfilter/*.o libswresample/*.o compat/*.o >> $PREFIX/build-make.log
    ;;
esac
${STRIP} --strip-unneeded $PREFIX/libffmpeg.so
echo "Done. "
