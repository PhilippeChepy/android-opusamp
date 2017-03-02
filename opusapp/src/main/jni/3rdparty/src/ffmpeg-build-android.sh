#!/bin/bash

export ANDROID_NDK="/Users/phil/Library/Android/android-ndk-r10c"
export ANDROID_NDK_TOOLCHAIN_VERSION=4.8

HOST_OS=$(uname -s)
case $HOST_OS in
    Darwin )
        HOST_SYSTEM="darwin-x86_64"
        ;;
    Linux )
        HOST_SYSTEM="linux-x86_64"
        ;;
    CYGWIN* | *_NT-* )
        HOST_SYSTEM="windows"
        ;;
esac

platform="$1"

echo ">> Building ffmpeg. "

SOURCE=`pwd`
TARGET_DIR=$SOURCE/build/
PREFIX="$TARGET_DIR$platform"

TOOLCHAIN=/tmp/android-cross-compilers
SYSROOT=$TOOLCHAIN/sysroot/

rm -rf $TOOLCHAIN

export PATH=$TOOLCHAIN/bin:$PATH

FFMPEG_FLAGS_COMMON="--disable-gpl \
--disable-programs \
--disable-doc \
--enable-optimizations \
--enable-pic \
--enable-libsoxr \
--disable-static \
--enable-shared \
--enable-asm \
--enable-yasm \
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
--enable-decoder=aac \
--enable-decoder=ac3 \
--enable-decoder=adpcm_ms \
--enable-decoder=adpcm_g726 \
--enable-decoder=alac \
--enable-decoder=ape \
--enable-decoder=flac \
--enable-decoder=gsm \
--enable-decoder=gsm_ms \
--enable-decoder=mp1 \
--enable-decoder=mp2 \
--enable-decoder=mp3 \
--enable-decoder=mpc7 \
--enable-decoder=mpc8 \
--enable-decoder=opus \
--enable-decoder=tta \
--enable-decoder=vorbis \
--enable-decoder=wavpack \
--enable-decoder=wmav1 \
--enable-decoder=wmav2 \
--enable-decoder=pcm_alaw \
--enable-decoder=pcm_dvd \
--enable-decoder=pcm_f32be \
--enable-decoder=pcm_f32le \
--enable-decoder=pcm_f64be \
--enable-decoder=pcm_f64le \
--enable-decoder=pcm_mulaw \
--enable-decoder=pcm_s16be \
--enable-decoder=pcm_s16le \
--enable-decoder=pcm_s16le_planar \
--enable-decoder=pcm_s24be \
--enable-decoder=pcm_s24le \
--enable-decoder=pcm_s32be \
--enable-decoder=pcm_s32le \
--enable-decoder=pcm_s8 \
--enable-decoder=pcm_u16be \
--enable-decoder=pcm_u16le \
--enable-decoder=pcm_u24be \
--enable-decoder=pcm_u24le \
--enable-decoder=rawvideo
--enable-decoder=tta \
--enable-decoder=vorbis \
--enable-decoder=wmav1 \
--enable-decoder=wmav2 \
--enable-decoder=wmalossless \
--enable-decoder=wmapro \
--enable-decoder=wmavoice \
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
--enable-demuxer=ac3 \
--enable-demuxer=ape \
--enable-demuxer=asf \
--enable-demuxer=flac \
--enable-demuxer=matroska \
--enable-demuxer=mp3 \
--enable-demuxer=mpc \
--enable-demuxer=mov \
--enable-demuxer=mpc8 \
--enable-demuxer=ogg \
--enable-demuxer=pcm_s16be \
--enable-demuxer=pcm_s16le \
--enable-demuxer=pcm_u16be \
--enable-demuxer=pcm_u16le \
--enable-demuxer=pcm_alaw \
--enable-demuxer=pcm_mulaw \
--enable-demuxer=tta \
--enable-demuxer=wav \
--enable-demuxer=wv \
--enable-demuxer=xwma \
--disable-parsers \
--enable-parser=mpegaudio \
--enable-parser=aac \
--disable-filters \
--enable-filter=aresample \
--enable-filter=aconvert \
--enable-zlib"

CFLAGS="-std=c99 -ffast-math -I../soxr/src/ -DANDROID -D__ANDROID__"
LDFLAGS="-lm -lz -Wl,-z,noexecstack,--gc-sections -L../../libs/$platform/ -lsoxr"

echo -n " * Cleaning previous build. "
rm -rf $PREFIX
mkdir -p $PREFIX
echo "Done."

echo -n " * Preparing environement for $platform arch. "

case $platform in
    x86 )
        export TOOLCHAIN_ARCH="x86"
        export TOOLCHAIN_OPT="--cpu=i686 --disable-amd3dnow --disable-amd3dnowext"
        export TOOLCHAIN_PREFIX="i686-linux-android-"
        export TOOLCHAIN_NAME="x86-4.9"
        export TOOLCHAIN_PLATFORM="android-9"

        export CFLAGS="$CFLAGS -O2 \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -fstrict-aliasing    \
        -funswitch-loops     \
        -finline-limit=300
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -no-canonical-prefixes \
        -fstack-protector"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes"

        export EXTRA_CFLAGS="-march=i686 -mtune=intel -mstackrealign -mssse3 -mfpmath=sse -m32"
        export EXTRA_LDFLAGS="-Wl,--icf=safe"
        ;;
    mips )
        export TOOLCHAIN_ARCH="mips"
        export TOOLCHAIN_OPT="--cpu=mips32r2 --disable-mipsfpu --disable-mipsdspr1 --disable-mipsdspr2"
        export TOOLCHAIN_PREFIX="mipsel-linux-android-"
        export TOOLCHAIN_NAME="${TOOLCHAIN_PREFIX}4.9"
        export TOOLCHAIN_PLATFORM="android-9"

        export CFLAGS="$CFLAGS -O2 \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -funswitch-loops     \
        -finline-limit=300 \
        -fpic \
        -fno-strict-aliasing \
        -finline-functions \
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -fmessage-length=0 \
        -fno-inline-functions-called-once \
        -fgcse-after-reload \
        -frerun-cse-after-loop \
        -frename-registers \
        -no-canonical-prefixes"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes"
        export EXTRA_CFLAGS="-fno-strict-aliasing -fmessage-length=0 -fno-inline-functions-called-once -frerun-cse-after-loop -frename-registers"
        export EXTRA_LDFLAGS=""
        ;;
    armeabi )
        export TOOLCHAIN_ARCH="arm"
        export TOOLCHAIN_OPT=""
        export TOOLCHAIN_PREFIX="arm-linux-androideabi-"
        export TOOLCHAIN_NAME="${TOOLCHAIN_PREFIX}4.9"
        export TOOLCHAIN_PLATFORM="android-8"

        export CFLAGS="$CFLAGS -march=armv5 -msoft-float -mthumb \
        -Os \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -fno-strict-aliasing \
        -finline-limit=64 \
        -fpic \
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -fstack-protector \
        -no-canonical-prefixes"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes"
        export EXTRA_CFLAGS=""
        export EXTRA_LDFLAGS="-Wl,--icf=safe"
        ;;
    armeabi-v7a )
        export TOOLCHAIN_ARCH="arm"
        export TOOLCHAIN_OPT=""
        export TOOLCHAIN_PREFIX="arm-linux-androideabi-"
        export TOOLCHAIN_NAME="${TOOLCHAIN_PREFIX}4.9"
        export TOOLCHAIN_PLATFORM="android-8"

        export CFLAGS="$CFLAGS -march=armv7-a -mfpu=vfpv3-d16 -mfloat-abi=softfp -O2 \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -fstrict-aliasing    \
        -funswitch-loops     \
        -finline-limit=300 \
        -fpic \
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -fstack-protector \
        -no-canonical-prefixes"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes -march=armv7-a -Wl,--fix-cortex-a8"
        export EXTRA_CFLAGS="-mfloat-abi=softfp -mfpu=vfpv3-d16 -marm -march=armv7-a"
        export EXTRA_LDFLAGS="-Wl,--icf=safe"
        ;;
    x86_64 )
        export TOOLCHAIN_ARCH="x86"
        export TOOLCHAIN_OPT="--cpu=x86_64 --disable-amd3dnow --disable-amd3dnowext"
        export TOOLCHAIN_PREFIX="x86_64-linux-android-"
        export TOOLCHAIN_NAME="${TOOLCHAIN_PREFIX}4.9"
        export TOOLCHAIN_PLATFORM="android-21"

        export CFLAGS="$CFLAGS -O2 \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -fstrict-aliasing    \
        -funswitch-loops     \
        -finline-limit=300
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -fstack-protector \
        -no-canonical-prefixes"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes"
        export EXTRA_CFLAGS="-march=x86-64 -msse4.2 -mpopcnt -m64 -mtune=intel"
        export EXTRA_LDFLAGS="-Wl,--icf=safe"
        ;;
    mips64el )
        export TOOLCHAIN_ARCH="mips"
        export TOOLCHAIN_OPT="--cpu=mips64r6"
        export TOOLCHAIN_PREFIX="mips64el-linux-android-"
        export TOOLCHAIN_NAME="${TOOLCHAIN_PREFIX}4.9"
        export TOOLCHAIN_PLATFORM="android-21"

        export CFLAGS="$CFLAGS -O2 \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -funswitch-loops     \
        -finline-limit=300
        -fpic \
        -fno-strict-aliasing \
        -finline-functions \
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -fmessage-length=0 \
        -fno-inline-functions-called-once \
        -fgcse-after-reload \
        -frerun-cse-after-loop \
        -frename-registers \
        -no-canonical-prefixes"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes"
        export EXTRA_CFLAGS=""
        export EXTRA_LDFLAGS=""
        ;;
    aarch64 )
        export TOOLCHAIN_ARCH="aarch64"
        export TOOLCHAIN_OPT=""
        export TOOLCHAIN_PREFIX="aarch64-linux-android-"
        export TOOLCHAIN_NAME="${TOOLCHAIN_PREFIX}4.9"
        export TOOLCHAIN_PLATFORM="android-21"

        export CFLAGS="$CFLAGS -O2 \
        -g \
        -DNDEBUG \
        -fomit-frame-pointer \
        -fstrict-aliasing    \
        -funswitch-loops     \
        -finline-limit=300 \
        -fpic \
        -ffunction-sections \
        -fdata-sections \
        -funwind-tables \
        -fstack-protector \
        -no-canonical-prefixes"
        export LDFLAGS="$LDFLAGS -no-canonical-prefixes"
        export EXTRA_CFLAGS="-mcpu=generic -mtune=generic -march=armv8-a"
        export EXTRA_LDFLAGS="-Wl,--icf=safe"
        ;;
    * )
        echo "FAILURE"
        echo "!!! Invalid arch “${platform}”"
        exit 1;
        ;;
esac
echo "Done."

export FFMPEG_FLAGS="--target-os=android --enable-cross-compile --cross-prefix=$TOOLCHAIN_PREFIX --arch=$TOOLCHAIN_ARCH $TOOLCHAIN_OPT --enable-runtime-cpudetect $FFMPEG_FLAGS_COMMON"


echo -n " * Preparing cross compiler... "
$ANDROID_NDK/build/tools/make-standalone-toolchain.sh --toolchain=${TOOLCHAIN_NAME} --platform=${TOOLCHAIN_PLATFORM} --system=$HOST_SYSTEM --install-dir=$TOOLCHAIN > $PREFIX/build-toolchain.log
echo "Done."

export CC=${TOOLCHAIN_PREFIX}gcc
export CXX=${TOOLCHAIN_PREFIX}g++
export LD=${TOOLCHAIN_PREFIX}ld
export AR=${TOOLCHAIN_PREFIX}ar
export STRIP=${TOOLCHAIN_PREFIX}strip

cd $SOURCE

FFMPEG_FLAGS="$FFMPEG_FLAGS --prefix=$PREFIX"

echo -n " * Configuring build... "
./configure $FFMPEG_FLAGS --extra-cflags="$CFLAGS $EXTRA_CFLAGS" --extra-ldflags="$LDFLAGS $EXTRA_LDFLAGS" > $PREFIX/build-configure.log 2> $PREFIX/build-configure.err.log
cp config.* $PREFIX
echo "Done. "

[ $PIPESTATUS == 0 ] || exit 1

echo -n " * Building library components... "
make clean > /dev/null
find . -name "*.o" -type f -delete
make -j5 install > $PREFIX/build-make.log 2> $PREFIX/build-make-errors.log || exit 1
echo "Done. "
