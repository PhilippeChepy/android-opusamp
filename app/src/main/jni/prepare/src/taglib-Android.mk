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
