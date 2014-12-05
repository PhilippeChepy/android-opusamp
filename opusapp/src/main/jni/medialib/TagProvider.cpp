/*
 * TagProvider.cpp
 *
 * Copyright (c) 2012, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */

#include <audioproperties.h>
#include <fileref.h>

#include <apefile.h>
#include <asffile.h>
#include <flacfile.h>
#include <mpcfile.h>
#include <mp4file.h>
#include <mpegfile.h>
#include <vorbisfile.h>
#include <wavpackfile.h>


#include <apetag.h>
#include <attachedpictureframe.h>

#include <id3v1genres.h>
#include <id3v2tag.h>
#include <tag.h>
#include <taglib.h>
#include <textidentificationframe.h>
#include <tstring.h>


#include <jni.h>


#include <android/log.h>

#define LOG_ERROR(log_tag, ...)   {__android_log_print(ANDROID_LOG_ERROR, log_tag, __VA_ARGS__); }
#define LOG_WARNING(log_tag, ...) {__android_log_print(ANDROID_LOG_ERROR, log_tag, __VA_ARGS__); }
#define LOG_DEBUG(log_tag, ...)   {__android_log_print(ANDROID_LOG_DEBUG, log_tag, __VA_ARGS__);}
#define LOG_INFO(log_tag, ...)    {__android_log_print(ANDROID_LOG_INFO, log_tag, __VA_ARGS__);}


#define LOG_TAG "TagProvider-JNI"

#ifdef __cplusplus
extern "C" {
#endif



static long ptr_to_id(void * ptr) {
	return (long) ptr;
}

static void * id_to_ptr(long id) {
	return (void *) id;
}



bool apeGetCover(TagLib::APE::Tag* tag, TagLib::ByteVector &target, bool getCover) {
	const TagLib::APE::ItemListMap& listMap = tag->itemListMap();

	if (listMap.contains("COVER ART (FRONT)")) {
		const TagLib::ByteVector nullStringTerminator(1, 0);

		TagLib::ByteVector item = listMap["COVER ART (FRONT)"].value();
		int pos = item.find(nullStringTerminator);	// Skip the filename

		if (++pos > 0) {
		    if (getCover) {
			    target = item.mid(pos);
			}
			return true;
		}
	}

	return false;
}

bool id3GetCover(TagLib::ID3v2::Tag* tag, TagLib::ByteVector &target, bool getCover) {
	const TagLib::ID3v2::FrameList& frameList = tag->frameList("APIC");

	if (!frameList.isEmpty()) {
	    TagLib::ID3v2::AttachedPictureFrame* frame = static_cast<TagLib::ID3v2::AttachedPictureFrame*>(frameList.front());

	    if (getCover) {
		    target = frame->picture();
		}
		return true;
	}

	return false;
}

bool asfGetCover(TagLib::ASF::File* file, TagLib::ByteVector &target, bool getCover)
{
	const TagLib::ASF::AttributeListMap& attrListMap = file->tag()->attributeListMap();

	if (attrListMap.contains("WM/Picture")) {
		const TagLib::ASF::AttributeList& attrList = attrListMap["WM/Picture"];

		if (!attrList.isEmpty()) {
		    if (getCover) {
			    TagLib::ASF::Picture wmpic = attrList[0].toPicture();
			    target = wmpic.picture();
			}
		    return true;
		}
	}

	return false;
}

bool flacGetCover(TagLib::FLAC::File* file, TagLib::ByteVector &target, bool getCover) {
	const TagLib::List<TagLib::FLAC::Picture*>& picList = file->pictureList();

	if (!picList.isEmpty()) {
	    if (getCover) {
		    TagLib::FLAC::Picture* pic = picList[0];
		    target = pic->data();
		}
		return true;
	}

	return false;
}

bool mp4GetCover(TagLib::MP4::File* file, TagLib::ByteVector &target, bool getCover)
{
	TagLib::MP4::Tag* tag = file->tag();
	if (tag->itemListMap().contains("covr")) {
		TagLib::MP4::CoverArtList coverList = tag->itemListMap()["covr"].toCoverArtList();

		if (coverList[0].data().size() > 0) {
		    if (getCover) {
			    target = coverList[0].data();
			}
			return true;
		}
	}

	return false;
}

bool findCoverArt(TagLib::FileRef &fileRef, TagLib::ByteVector &target, bool getCover) {
    bool found = false;
    TagLib::File * sourceFile = fileRef.file();

    if (TagLib::MPEG::File* file = dynamic_cast<TagLib::MPEG::File*>(sourceFile)) {
		if (file->hasID3v2Tag()) {
			found = id3GetCover(file->ID3v2Tag(), target, getCover);
		}

		if (!found && file->hasAPETag()) {
			found = apeGetCover(file->APETag(), target, getCover);
		}
	}
	else if (TagLib::MP4::File* file = dynamic_cast<TagLib::MP4::File*>(sourceFile)) {
		if (file->tag()) {
			found = mp4GetCover(file, target, getCover);
		}
	}
	else if (TagLib::FLAC::File* file = dynamic_cast<TagLib::FLAC::File*>(sourceFile)) {
		found = flacGetCover(file, target, getCover);

		if (!found && file->ID3v2Tag()) {
			found = id3GetCover(file->ID3v2Tag(), target, getCover);
		}
	}
	else if (TagLib::ASF::File* file = dynamic_cast<TagLib::ASF::File*>(sourceFile)) {
		found = asfGetCover(file, target, getCover);
	}
	else if (TagLib::APE::File* file = dynamic_cast<TagLib::APE::File*>(sourceFile)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), target, getCover);
		}
	}
	else if (TagLib::MPC::File* file = dynamic_cast<TagLib::MPC::File*>(sourceFile)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), target, getCover);
		}
	}
	else if (TagLib::WavPack::File* file = dynamic_cast<TagLib::WavPack::File*>(sourceFile)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), target, getCover);
		}
	}

	return found;
}

JNIEXPORT void JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_tagsRead(JNIEnv * env, jclass classLibraryScannerService, jstring path) {
	jfieldID fieldTagDuration = env->GetStaticFieldID(classLibraryScannerService, "tagDuration", "I");
	jfieldID fieldTagBitrate = env->GetStaticFieldID(classLibraryScannerService, "tagBitrate", "I");
	jfieldID fieldTagSamplerate = env->GetStaticFieldID(classLibraryScannerService, "tagSamplerate", "I");

	jfieldID fieldTagTitle = env->GetStaticFieldID(classLibraryScannerService, "tagTitle", "Ljava/lang/String;");
	jfieldID fieldTagArtist = env->GetStaticFieldID(classLibraryScannerService, "tagArtist", "Ljava/lang/String;");
//	jfieldID fieldTagComposer = env->GetStaticFieldID(classLibraryScannerService, "tagComposer", "Ljava/lang/String;");
//	jfieldID fieldTagAlbumArtist = env->GetStaticFieldID(classLibraryScannerService, "tagAlbumArtist", "Ljava/lang/String;");
	jfieldID fieldTagAlbum = env->GetStaticFieldID(classLibraryScannerService, "tagAlbum", "Ljava/lang/String;");
	jfieldID fieldTagGenre = env->GetStaticFieldID(classLibraryScannerService, "tagGenre", "Ljava/lang/String;");

	jfieldID fieldTagYear = env->GetStaticFieldID(classLibraryScannerService, "tagYear", "I");
	jfieldID fieldTagTrack = env->GetStaticFieldID(classLibraryScannerService, "tagTrack", "I");
//	jfieldID fieldTagDisc = env->GetStaticFieldID(classLibraryScannerService, "tagDisc", "I");
//	jfieldID fieldTagBpm = env->GetStaticFieldID(classLibraryScannerService, "tagBpm", "I");

	jfieldID fieldTagComment = env->GetStaticFieldID(classLibraryScannerService, "tagComment", "Ljava/lang/String;");
//	jfieldID fieldTagLyrics = env->GetStaticFieldID(classLibraryScannerService, "tagLyrics", "Ljava/lang/String;");

	jfieldID fieldTagHasEmbeddedArt = env->GetStaticFieldID(classLibraryScannerService, "hasEmbeddedArt", "Z");

	const char * media_path = env->GetStringUTFChars(path, (jboolean *)0);

    TagLib::FileRef file(media_path);
    TagLib::ByteVector coverData;

	if(!file.isNull() && file.file()->isValid()) {
		TagLib::Tag *tag = file.tag();
		TagLib::AudioProperties *properties = file.audioProperties();

		if(tag) {
		  	env->SetStaticObjectField(classLibraryScannerService, fieldTagTitle, env->NewStringUTF(tag->title().toCString(true)));
			env->SetStaticObjectField(classLibraryScannerService, fieldTagArtist, env->NewStringUTF(tag->artist().toCString(true)));
			//env->SetStaticObjectField(classLibraryScannerService, fieldTagComposer, env->NewStringUTF());
			//env->SetStaticObjectField(classLibraryScannerService, fieldTagAlbumArtist, env->NewStringUTF());
			env->SetStaticObjectField(classLibraryScannerService, fieldTagAlbum, env->NewStringUTF(tag->album().toCString(true)));
			env->SetStaticObjectField(classLibraryScannerService, fieldTagGenre, env->NewStringUTF(tag->genre().toCString(true)));

			env->SetStaticIntField(classLibraryScannerService, fieldTagYear, tag->year());
			env->SetStaticIntField(classLibraryScannerService, fieldTagTrack, tag->track());
			//env->SetStaticIntField(classLibraryScannerService, fieldTagDisc, tag->disc());
			//env->SetStaticIntField(classLibraryScannerService, fieldTagBpm, tag->bpm());

			env->SetStaticObjectField(classLibraryScannerService, fieldTagComment, env->NewStringUTF(tag->comment().toCString(true)));
			//env->SetStaticObjectField(classLibraryScannerService, fieldTagLyrics, env->NewStringUTF());
			env->SetStaticBooleanField(classLibraryScannerService, fieldTagHasEmbeddedArt, findCoverArt(file, coverData, false));
		}

		if(properties) {
			env->SetStaticIntField(classLibraryScannerService, fieldTagBitrate, properties->bitrate());
			env->SetStaticIntField(classLibraryScannerService, fieldTagSamplerate, properties->sampleRate());
			env->SetStaticIntField(classLibraryScannerService, fieldTagDuration, properties->length());
			// properties->channels();
		}
	}

	env->ReleaseStringUTFChars(path, media_path);
}

JNIEXPORT void JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_tagsWrite(JNIEnv * env, jclass classLibraryScannerService, jstring path) {

	jfieldID fieldTagTitle = env->GetStaticFieldID(classLibraryScannerService, "tagTitle", "Ljava/lang/String;");
	jfieldID fieldTagArtist = env->GetStaticFieldID(classLibraryScannerService, "tagArtist", "Ljava/lang/String;");
//	jfieldID fieldTagComposer = env->GetStaticFieldID(classLibraryScannerService, "tagComposer", "Ljava/lang/String;");
//	jfieldID fieldTagAlbumArtist = env->GetStaticFieldID(classLibraryScannerService, "tagAlbumArtist", "Ljava/lang/String;");
	jfieldID fieldTagAlbum = env->GetStaticFieldID(classLibraryScannerService, "tagAlbum", "Ljava/lang/String;");
	jfieldID fieldTagGenre = env->GetStaticFieldID(classLibraryScannerService, "tagGenre", "Ljava/lang/String;");

	jfieldID fieldTagYear = env->GetStaticFieldID(classLibraryScannerService, "tagYear", "I");
	jfieldID fieldTagTrack = env->GetStaticFieldID(classLibraryScannerService, "tagTrack", "I");
//	jfieldID fieldTagDisc = env->GetStaticFieldID(classLibraryScannerService, "tagDisc", "I");
//	jfieldID fieldTagBpm = env->GetStaticFieldID(classLibraryScannerService, "tagBpm", "I");

	jfieldID fieldTagComment = env->GetStaticFieldID(classLibraryScannerService, "tagComment", "Ljava/lang/String;");
//	jfieldID fieldTagLyrics = env->GetStaticFieldID(classLibraryScannerService, "tagLyrics", "Ljava/lang/String;");

	const char * media_path = env->GetStringUTFChars(path, (jboolean *)0);
    TagLib::FileRef file(TagLib::FileRef::create(media_path));
    LOG_ERROR(LOG_TAG, "writing tags for file '%s'", media_path);

	if(!file.isNull() && file.tag()) { //} && file.file()->isValid()) {
	    LOG_ERROR(LOG_TAG, "writing tags...");

		TagLib::Tag *tag = file.tag();

        char const * tagContent = NULL;
        jstring tagField;
        jboolean isCopy = JNI_FALSE;

        tagField = (jstring) env->GetStaticObjectField(classLibraryScannerService, fieldTagTitle);
        tagContent = env->GetStringUTFChars(tagField, &isCopy);
        tag->setTitle(tagContent);
        LOG_ERROR(LOG_TAG, "setTitle = %s", tagContent)
        env->ReleaseStringUTFChars(tagField, tagContent);

        tagField = (jstring) env->GetStaticObjectField(classLibraryScannerService, fieldTagArtist);
        tagContent = env->GetStringUTFChars(tagField, &isCopy);
        tag->setArtist(tagContent);
        LOG_ERROR(LOG_TAG, "setArtist = %s", tagContent)
        env->ReleaseStringUTFChars(tagField, tagContent);

        tagField = (jstring) env->GetStaticObjectField(classLibraryScannerService, fieldTagAlbum);
        tagContent = env->GetStringUTFChars(tagField, &isCopy);
        tag->setAlbum(tagContent);
        LOG_ERROR(LOG_TAG, "setArtist = %s", tagContent)
        env->ReleaseStringUTFChars(tagField, tagContent);

        tagField = (jstring) env->GetStaticObjectField(classLibraryScannerService, fieldTagGenre);
        tagContent = env->GetStringUTFChars(tagField, &isCopy);
        tag->setGenre(tagContent);
        env->ReleaseStringUTFChars(tagField, tagContent);

        tag->setYear(env->GetStaticIntField(classLibraryScannerService, fieldTagYear));
        tag->setTrack(env->GetStaticIntField(classLibraryScannerService, fieldTagTrack));

        tagField = (jstring) env->GetStaticObjectField(classLibraryScannerService, fieldTagComment);
        tagContent = env->GetStringUTFChars(tagField, &isCopy);
        tag->setComment(tagContent);
        env->ReleaseStringUTFChars(tagField, tagContent);

        // fieldTagComposer, fieldTagAlbumArtist, fieldTagDisc, fieldTagBpm, fieldTagLyrics

        file.save();
	}

	env->ReleaseStringUTFChars(path, media_path);
}

class CoverStreamContext {
public:
    TagLib::FileRef mFileRef;
    TagLib::ByteVector mCoverData;
    bool mIsValid;

public:
    CoverStreamContext(TagLib::FileName path) : mFileRef(path) {
        mIsValid = !mFileRef.isNull() && mFileRef.file()->isValid();
        mIsValid = mIsValid && findCoverArt(mFileRef, mCoverData, true);
    }

    bool isValid() {
        return mIsValid;
    }
};

JNIEXPORT jlong JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_coverInputStreamOpen(JNIEnv * env, jclass classLibraryScannerService, jstring path) {
    const char * mediaPath = env->GetStringUTFChars(path, (jboolean *)0);
    CoverStreamContext * streamContext = new CoverStreamContext(mediaPath);
    env->ReleaseStringUTFChars(path, mediaPath);

    if (streamContext->isValid()) {
        return ptr_to_id(streamContext);
    }

    delete streamContext;
    return 0;
}

JNIEXPORT jint JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_coverInputStreamClose(JNIEnv * env, jclass classLibraryScannerService, jlong context) {
    CoverStreamContext * streamContext = (CoverStreamContext *) id_to_ptr(context);

    if (streamContext != NULL) {
        delete streamContext;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_coverInputStreamReadGetCount(JNIEnv * env, jclass classLibraryScannerService, jlong context) {
    CoverStreamContext * streamContext = (CoverStreamContext *) id_to_ptr(context);

    if (streamContext != NULL) {
        return streamContext->mCoverData.size();
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_coverInputStreamReadSingle(JNIEnv * env, jclass classLibraryScannerService, jlong context, jint position) {
    CoverStreamContext * streamContext = (CoverStreamContext *) id_to_ptr(context);

    if (streamContext != NULL) {
        return streamContext->mCoverData.at(position);
    }

    return -1;
}

JNIEXPORT jint JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_coverInputStreamReadArray(JNIEnv * env, jclass classLibraryScannerService,
    jlong context, jbyteArray target, jint offset, jint length, jint nativePos) {
    CoverStreamContext * streamContext = (CoverStreamContext *) id_to_ptr(context);

    if (streamContext != NULL) {
        jbyte * coverData = (jbyte *) streamContext->mCoverData.data();
        // TODO: add buffer overflow check
        env->SetByteArrayRegion(target, offset, length, &coverData[nativePos]);
        return 0;
    }

    return -1;
}

#ifdef __cplusplus
}
#endif
