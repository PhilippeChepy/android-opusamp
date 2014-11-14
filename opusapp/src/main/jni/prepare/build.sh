#!/bin/sh
PATH=/mnt/net/devel/tools/android-ndk:$PATH


rm jni -rf > /dev/null
rm libs -rf > /dev/null
rm obj -rf > /dev/null

tar xf src/ffmpeg-2.4.3.tar.bz2
tar xf src/soxr-0.1.1-Source.tar.xz
tar xf src/taglib-1.9.1.tar.gz

mkdir jni

mv ffmpeg-2.4.3 jni/ffmpeg
mv soxr-0.1.1-Source jni/soxr
mv taglib-1.9.1 jni/taglib

cp src/ffmpeg-Android.mk jni/ffmpeg/Android.mk
cp src/ffmpeg-build-android.sh jni/ffmpeg/build-android.sh

cp src/step1-soxr-Android.mk jni/soxr/Android.mk
cp src/soxr-config.h jni/soxr/src/soxr-config.h

cp src/taglib-Android.mk jni/taglib/Android.mk
mkdir jni/taglib/include
cp src/taglib_config.h jni/taglib/include/
cd jni/taglib/taglib
find . -name \*.h -exec cp {} ../include/ \;
find . -name \*.tcc -exec cp {} ../include/ \;
cd ../../..

cd jni

cp ../src/Application.mk Application.mk
cp ../src/step1-Android.mk Android.mk

ndk-build

cd ../jni/ffmpeg
./build-android.sh armeabi
./build-android.sh armeabi-v7a
./build-android.sh mips
./build-android.sh x86

cd ../..

mkdir jni/soxr/build/
cp libs/* jni/soxr/build/ -R

rm jni/soxr/Android.mk
cp src/step2-soxr-Android.mk jni/soxr/Android.mk
cp src/step2-Android.mk jni/Android.mk

cd jni/
ndk-build

cd ../..

rm prebuilt-ffmpeg -Rf
rm prebuilt-soxr -Rf
rm prebuilt-taglib -Rf

# copying ffmpeg from prebuilt source
mkdir -p ffmpeg/libs
cd prepare/libs
find . -name libffmpeg.so -exec cp --parents {} ../../ffmpeg/libs \;
cd ../..
mkdir -p ffmpeg/armeabi/libs
mv ffmpeg/libs/armeabi/*.so ffmpeg/armeabi/libs/
cp prepare/jni/ffmpeg/build/armeabi/include ffmpeg/armeabi/ -R
mkdir -p ffmpeg/armeabi-v7a/libs
mv ffmpeg/libs/armeabi-v7a/*.so ffmpeg/armeabi-v7a/libs/
cp prepare/jni/ffmpeg/build/armeabi-v7a/include ffmpeg/armeabi-v7a/ -R
mkdir -p ffmpeg/x86/libs
mv ffmpeg/libs/x86/*.so ffmpeg/x86/libs/
cp prepare/jni/ffmpeg/build/x86/include ffmpeg/x86/ -R
mkdir -p ffmpeg/mips/libs
mv ffmpeg/libs/mips/*.so ffmpeg/mips/libs/
cp prepare/jni/ffmpeg/build/mips/include ffmpeg/mips/ -R
rm ffmpeg/libs -Rf
cp prepare/src/prebuilt-ffmpeg-Android.mk ffmpeg/Android.mk

# copying soxr from prebuilt source
mkdir -p soxr/libs
cd prepare/libs
find . -name libsoxr.so -exec cp --parents {} ../../soxr/libs \;
cd ../..
mkdir -p soxr/armeabi/libs
mv soxr/libs/armeabi/*.so soxr/armeabi/libs/
mkdir -p soxr/armeabi-v7a/libs
mv soxr/libs/armeabi-v7a/*.so soxr/armeabi-v7a/libs/
mkdir -p soxr/x86/libs
mv soxr/libs/x86/*.so soxr/x86/libs/
mkdir -p soxr/mips/libs
mv soxr/libs/mips/*.so soxr/mips/libs/
rm soxr/libs -Rf
mkdir soxr/include
cp prepare/jni/soxr/src/* soxr/include
cp prepare/src/prebuilt-soxr-Android.mk soxr/Android.mk

# copying taglib from prebuilt source
mkdir -p taglib/libs
cd prepare/libs
find . -name libtaglib.so -exec cp --parents {} ../../taglib/libs \;
cd ../..
mkdir -p taglib/armeabi/libs
mv taglib/libs/armeabi/*.so taglib/armeabi/libs/
mkdir -p taglib/armeabi-v7a/libs
mv taglib/libs/armeabi-v7a/*.so taglib/armeabi-v7a/libs/
mkdir -p taglib/x86/libs
mv taglib/libs/x86/*.so taglib/x86/libs/
mkdir -p taglib/mips/libs
mv taglib/libs/mips/*.so taglib/mips/libs/
rm taglib/libs -Rf
mkdir taglib/include
cp prepare/jni/taglib/include/* taglib/include
cp prepare/src/prebuilt-taglib-Android.mk taglib/Android.mk

mv ffmpeg prebuilt-ffmpeg
mv soxr prebuilt-soxr
mv taglib prebuilt-taglib
