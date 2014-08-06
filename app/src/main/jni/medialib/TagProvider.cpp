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



typedef struct {
    TagLib::File * file;
    TagLib::ByteVector coverData;
} inputstream_context_s;



static long ptr_to_id(void * ptr) {
	return (long) ptr;
}

static void * id_to_ptr(long id) {
	return (void *) id;
}



bool apeGetCover(TagLib::APE::Tag* tag, inputstream_context_s * target, bool getCover) {
	const TagLib::APE::ItemListMap& listMap = tag->itemListMap();

	if (listMap.contains("COVER ART (FRONT)")) {
		const TagLib::ByteVector nullStringTerminator(1, 0);

		TagLib::ByteVector item = listMap["COVER ART (FRONT)"].value();
		int pos = item.find(nullStringTerminator);	// Skip the filename

		if (++pos > 0) {
		    if (getCover) {
			    target->coverData = item.mid(pos);
			}
			return true;
		}
	}

	return false;
}

bool id3GetCover(TagLib::ID3v2::Tag* tag, inputstream_context_s * target, bool getCover) {
	const TagLib::ID3v2::FrameList& frameList = tag->frameList("APIC");

	if (!frameList.isEmpty()) {
	    TagLib::ID3v2::AttachedPictureFrame* frame = static_cast<TagLib::ID3v2::AttachedPictureFrame*>(frameList.front());

	    if (getCover) {
		    target->coverData = frame->picture();
		}
		return true;
	}

	return false;
}

bool asfGetCover(TagLib::ASF::File* file, inputstream_context_s * target, bool getCover)
{
	const TagLib::ASF::AttributeListMap& attrListMap = file->tag()->attributeListMap();

	if (attrListMap.contains("WM/Picture")) {
		const TagLib::ASF::AttributeList& attrList = attrListMap["WM/Picture"];

		if (!attrList.isEmpty()) {
		    if (getCover) {
			    TagLib::ASF::Picture wmpic = attrList[0].toPicture();
			    target->coverData = wmpic.picture();
			}
		    return true;
		}
	}

	return false;
}

bool flacGetCover(TagLib::FLAC::File* file, inputstream_context_s * target, bool getCover) {
	const TagLib::List<TagLib::FLAC::Picture*>& picList = file->pictureList();

	if (!picList.isEmpty()) {
	    if (getCover) {
		    TagLib::FLAC::Picture* pic = picList[0];
		    target->coverData = pic->data();
		}
		return true;
	}

	return false;
}

bool mp4GetCover(TagLib::MP4::File* file, inputstream_context_s * target, bool getCover)
{
	TagLib::MP4::Tag* tag = file->tag();
	if (tag->itemListMap().contains("covr")) {
		TagLib::MP4::CoverArtList coverList = tag->itemListMap()["covr"].toCoverArtList();

		if (coverList[0].data().size() > 0) {
		    if (getCover) {
			    target->coverData = coverList[0].data();
			}
			return true;
		}
	}

	return false;
}

bool findCoverArt(inputstream_context_s * inputstream_context, bool getCover) {
    bool found = false;

    if (TagLib::MPEG::File* file = dynamic_cast<TagLib::MPEG::File*>(inputstream_context->file)) {
		if (file->hasID3v2Tag()) {
			found = id3GetCover(file->ID3v2Tag(), inputstream_context, getCover);
		}

		if (!found && file->hasAPETag()) {
			found = apeGetCover(file->APETag(), inputstream_context, getCover);
		}
	}
	else if (TagLib::MP4::File* file = dynamic_cast<TagLib::MP4::File*>(inputstream_context->file)) {
		if (file->tag()) {
			found = mp4GetCover(file, inputstream_context, getCover);
		}
	}
	else if (TagLib::FLAC::File* file = dynamic_cast<TagLib::FLAC::File*>(inputstream_context->file)) {
		found = flacGetCover(file, inputstream_context, getCover);

		if (!found && file->ID3v2Tag()) {
			found = id3GetCover(file->ID3v2Tag(), inputstream_context, getCover);
		}
	}
	else if (TagLib::ASF::File* file = dynamic_cast<TagLib::ASF::File*>(inputstream_context->file)) {
		found = asfGetCover(file, inputstream_context, getCover);
	}
	else if (TagLib::APE::File* file = dynamic_cast<TagLib::APE::File*>(inputstream_context->file)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), inputstream_context, getCover);
		}
	}
	else if (TagLib::MPC::File* file = dynamic_cast<TagLib::MPC::File*>(inputstream_context->file)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), inputstream_context, getCover);
		}
	}
	else if (TagLib::WavPack::File* file = dynamic_cast<TagLib::WavPack::File*>(inputstream_context->file)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), inputstream_context, getCover);
		}
	}

	return found;
}

JNIEXPORT void JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_tagsRead(JNIEnv * env, jclass classLibraryScannerService, jstring path) {
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

    inputstream_context_s inputstream_context;
    inputstream_context.file = TagLib::FileRef::create(media_path);

   	TagLib::File * currentFile = inputstream_context.file;

	if(currentFile && currentFile->isValid()) {
		TagLib::Tag *tag = currentFile->tag();
		TagLib::AudioProperties *properties = currentFile->audioProperties();

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
			env->SetStaticBooleanField(classLibraryScannerService, fieldTagHasEmbeddedArt, findCoverArt(&inputstream_context, false));
		}

		if(properties) {
			env->SetStaticIntField(classLibraryScannerService, fieldTagBitrate, properties->bitrate());
			env->SetStaticIntField(classLibraryScannerService, fieldTagSamplerate, properties->sampleRate());
			env->SetStaticIntField(classLibraryScannerService, fieldTagDuration, properties->length());
			// properties->channels();
		}
		delete currentFile;
	}

	env->ReleaseStringUTFChars(path, media_path);
}

JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_coverInputStreamOpen(JNIEnv * env, jclass classLibraryScannerService, jstring path) {
    inputstream_context_s * inputstream_context = new inputstream_context_s();

    const char * media_path = env->GetStringUTFChars(path, (jboolean *)0);
    inputstream_context->file = TagLib::FileRef::create(media_path);

    if (findCoverArt(inputstream_context, true)) {
        return ptr_to_id(inputstream_context);
    }

    delete inputstream_context;

    return 0;
}

JNIEXPORT jint JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_coverInputStreamClose(JNIEnv * env, jclass classLibraryScannerService, jlong context) {
    inputstream_context_s * inputstream_context = (inputstream_context_s *) id_to_ptr(context);

    if (inputstream_context != NULL) {
        delete inputstream_context;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_coverInputStreamReadSetPosition(JNIEnv * env, jclass classLibraryScannerService, jlong context, jint position) {
    inputstream_context_s * inputstream_context = (inputstream_context_s *) id_to_ptr(context);

    return -1;
}

JNIEXPORT jint JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_coverInputStreamReadGetCount(JNIEnv * env, jclass classLibraryScannerService, jlong context) {
    inputstream_context_s * inputstream_context = (inputstream_context_s *) id_to_ptr(context);

    if (inputstream_context != NULL) {
        return inputstream_context->coverData.size();
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_coverInputStreamReadSingle(JNIEnv * env, jclass classLibraryScannerService, jlong context, jint position) {
    inputstream_context_s * inputstream_context = (inputstream_context_s *) id_to_ptr(context);

    if (inputstream_context != NULL) {
        int result = inputstream_context->coverData.at(position);
        return result;
    }

    return -1;
}

JNIEXPORT jint JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_coverInputStreamReadArray(JNIEnv * env, jclass classLibraryScannerService,
    jlong context, jbyteArray target, jint offset, jint length, jint nativePos) {
    inputstream_context_s * inputstream_context = (inputstream_context_s *) id_to_ptr(context);

    if (inputstream_context != NULL) {
        jbyte * coverData = (jbyte *) inputstream_context->coverData.data();
        // TODO: add buffer overflow check
        env->SetByteArrayRegion(target, offset, length, &coverData[nativePos]);
        return 0;
    }

    return -1;
}

#ifdef __cplusplus
}
#endif
