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
#include <oggflacfile.h>
#include <opusfile.h>
#include <speexfile.h>
#include <vorbisfile.h>
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

#include "Base64.h"

#include <iostream>
#include <fstream>

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

bool apeGetCover(TagLib::APE::Tag* tag, const char * saveTarget, bool getCover) {
	const TagLib::APE::ItemListMap& listMap = tag->itemListMap();

	if (listMap.contains("COVER ART (FRONT)")) {
		const TagLib::ByteVector nullStringTerminator(1, 0);

		TagLib::ByteVector item = listMap["COVER ART (FRONT)"].value();
		int pos = item.find(nullStringTerminator);	// Skip the filename

		if (++pos > 0) {
		    if (getCover) {
		        std::ofstream outfile(saveTarget);
			    outfile << item.mid(pos);
			}
			return true;
		}
	}

	return false;
}


bool oggGetCover(TagLib::Ogg::XiphComment* tag, const char * saveTarget, bool getCover) {
	const TagLib::Ogg::FieldListMap& listMap = tag->fieldListMap();

	if (tag->contains("METADATA_BLOCK_PICTURE")) {
		TagLib::String item = listMap["METADATA_BLOCK_PICTURE"].front();

        if (getCover) {
            std::string data = base64_decode(item.to8Bit(false));
            std::ofstream outfile(saveTarget);
            outfile << TagLib::FLAC::Picture(TagLib::ByteVector(data.c_str(), data.length())).data();
        }

        return true;
	}
	else if (tag->contains("COVERART")) {
	    TagLib::String item = listMap["COVERART"].front();

        if (getCover) {
            std::string data = base64_decode(item.to8Bit(false));
            std::ofstream outfile(saveTarget);
            outfile << TagLib::FLAC::Picture(TagLib::ByteVector(data.c_str(), data.length())).data();
        }

        return true;
	}

	return false;
}

bool id3GetCover(TagLib::ID3v2::Tag* tag, const char * saveTarget, bool getCover) {
	const TagLib::ID3v2::FrameList& frameList = tag->frameList("APIC");

	if (!frameList.isEmpty()) {
	    TagLib::ID3v2::AttachedPictureFrame* frame = static_cast<TagLib::ID3v2::AttachedPictureFrame*>(frameList.front());

	    if (getCover) {
		    std::ofstream outfile(saveTarget);
            outfile << frame->picture();
		}
		return true;
	}

	return false;
}

bool asfGetCover(TagLib::ASF::File* file, const char * saveTarget, bool getCover)
{
	const TagLib::ASF::AttributeListMap& attrListMap = file->tag()->attributeListMap();

	if (attrListMap.contains("WM/Picture")) {
		const TagLib::ASF::AttributeList& attrList = attrListMap["WM/Picture"];

		if (!attrList.isEmpty()) {
		    if (getCover) {
			    TagLib::ASF::Picture wmpic = attrList[0].toPicture();
			    std::ofstream outfile(saveTarget);
                outfile << wmpic.picture();
			}
		    return true;
		}
	}

	return false;
}

bool flacGetCover(TagLib::FLAC::File* file, const char * saveTarget, bool getCover) {
	const TagLib::List<TagLib::FLAC::Picture*>& picList = file->pictureList();

	if (!picList.isEmpty()) {
	    if (getCover) {
		    TagLib::FLAC::Picture* pic = picList[0];
		    std::ofstream outfile(saveTarget);
            outfile << pic->data();
		}
		return true;
	}

	return false;
}

bool mp4GetCover(TagLib::MP4::File* file, const char * saveTarget, bool getCover)
{
	TagLib::MP4::Tag* tag = file->tag();
	if (tag->itemListMap().contains("covr")) {
		TagLib::MP4::CoverArtList coverList = tag->itemListMap()["covr"].toCoverArtList();

		if (coverList[0].data().size() > 0) {
		    if (getCover) {
			    std::ofstream outfile(saveTarget);
                outfile << coverList[0].data();
			}
			return true;
		}
	}

	return false;
}

bool findCoverArt(TagLib::FileRef &fileRef, const char * saveTarget, bool getCover) {
    bool found = false;
    TagLib::File * sourceFile = fileRef.file();

    if (TagLib::MPEG::File* file = dynamic_cast<TagLib::MPEG::File*>(sourceFile)) {
		if (file->hasID3v2Tag()) {
			found = id3GetCover(file->ID3v2Tag(), saveTarget, getCover);
		}

		if (!found && file->hasAPETag()) {
			found = apeGetCover(file->APETag(), saveTarget, getCover);
		}
	}
	else if (TagLib::Ogg::FLAC::File* file = dynamic_cast<TagLib::Ogg::FLAC::File*>(sourceFile)) {
	    if (file->tag()) {
	        found = oggGetCover(file->tag(), saveTarget, getCover);
	    }
	}
	else if (TagLib::Ogg::Vorbis::File* file = dynamic_cast<TagLib::Ogg::Vorbis::File*>(sourceFile)) {
	    if (file->tag()) {
	        found = oggGetCover(file->tag(), saveTarget, getCover);
	    }
	}
	else if (TagLib::Ogg::Speex::File* file = dynamic_cast<TagLib::Ogg::Speex::File*>(sourceFile)) {
	    if (file->tag()) {
	        found = oggGetCover(file->tag(), saveTarget, getCover);
	    }
	}
	else if (TagLib::Ogg::Opus::File* file = dynamic_cast<TagLib::Ogg::Opus::File*>(sourceFile)) {
	    if (file->tag()) {
	        found = oggGetCover(file->tag(), saveTarget, getCover);
	    }
	}
	else if (TagLib::MP4::File* file = dynamic_cast<TagLib::MP4::File*>(sourceFile)) {
		if (file->tag()) {
			found = mp4GetCover(file, saveTarget, getCover);
		}
	}
	else if (TagLib::FLAC::File* file = dynamic_cast<TagLib::FLAC::File*>(sourceFile)) {
		found = flacGetCover(file, saveTarget, getCover);

		if (!found && file->ID3v2Tag()) {
			found = id3GetCover(file->ID3v2Tag(), saveTarget, getCover);
		}
	}
	else if (TagLib::ASF::File* file = dynamic_cast<TagLib::ASF::File*>(sourceFile)) {
		found = asfGetCover(file, saveTarget, getCover);
	}
	else if (TagLib::APE::File* file = dynamic_cast<TagLib::APE::File*>(sourceFile)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), saveTarget, getCover);
		}
	}
	else if (TagLib::MPC::File* file = dynamic_cast<TagLib::MPC::File*>(sourceFile)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), saveTarget, getCover);
		}
	}
	else if (TagLib::WavPack::File* file = dynamic_cast<TagLib::WavPack::File*>(sourceFile)) {
		if (file->APETag()) {
			found = apeGetCover(file->APETag(), saveTarget, getCover);
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
			env->SetStaticBooleanField(classLibraryScannerService, fieldTagHasEmbeddedArt, findCoverArt(file, 0, false));
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

JNIEXPORT jint JNICALL Java_net_opusapp_player_utils_jni_JniMediaLib_saveCover(JNIEnv * env, jclass classLibraryScannerService, jstring path, jstring coverSavePath) {
	const char * media_path = env->GetStringUTFChars(path, (jboolean *)0);
	const char * saveTarget = env->GetStringUTFChars(coverSavePath, (jboolean *)0);

    TagLib::FileRef file(media_path);

	if(!file.isNull() && file.file()->isValid()) {
		TagLib::Tag *tag = file.tag();

		if(tag) {
		    return findCoverArt(file, saveTarget, true);
		}
    }
    return -1;
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

#ifdef __cplusplus
}
#endif
