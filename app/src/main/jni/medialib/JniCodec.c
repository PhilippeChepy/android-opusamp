/*
 * JniCodec.c
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

#include <jni.h>

#include <audio_engine/engine.h>
#include <audio_engine/outputs/audiotrack.h> /* this output needs a VM ref */
#include <audio_engine/utils/log.h>
#include <audio_engine/utils/memory.h>

#include <inttypes.h>

#define LOG_TAG "AudioEnginePlayback-JNI"

static JavaVMAttachArgs vmAttach;

static long ptr_to_id(void * ptr) {
	return (long) ptr;
}

static void * id_to_ptr(long id) {
	return (void *) id;
}

void playbackEndedCallback(engine_stream_context_s * stream) {
	JavaVM * vm = stream->engine->vm;
	jobject obj = stream->engine->obj;
	jclass cls  = stream->engine->cls;

    vmAttach.version = JNI_VERSION_1_6;  /* must be JNI_VERSION_1_2 */
    vmAttach.name = "JNICodec-Thread";    /* the name of the thread as a modified UTF-8 string, or NULL */
    vmAttach.group = NULL; /* global ref of a ThreadGroup object, or NULL */

	JNIEnv * env;
	int getEnvStat = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
	if (getEnvStat == JNI_EDETACHED) {
		if ((*vm)->AttachCurrentThread(vm, &env, &vmAttach) != 0) {
			LOG_ERROR(LOG_TAG, "playbackEndedCallback() jni: AttachCurrentThread() failed");
		}
	} else if (getEnvStat == JNI_EVERSION) {
		LOG_ERROR(LOG_TAG, "playbackEndedCallback() jni: GetEnv() unsupported version");
	}

	jmethodID methodPlaybackEndNotification = (*env)->GetMethodID(env, cls, "playbackEndNotification", "()V");
	(*env)->CallVoidMethod(env, obj, methodPlaybackEndNotification);

	(*vm)->DetachCurrentThread(vm);
}

void playbackTimestampCallback(engine_stream_context_s * stream, int64_t played) {
	JavaVM * vm = stream->engine->vm;
	jobject obj = stream->engine->obj;
	jclass cls  = stream->engine->cls;

	JNIEnv * env;
	int audiotrack_thread = 0;

	if (stream->engine->output->engine_get_name == audiotrack_get_output()->engine_get_name) {
		audiotrack_thread = 1;
	}

	if (audiotrack_thread) {
		int getEnvStat = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
		if (getEnvStat == JNI_EDETACHED) {
			if ((*vm)->AttachCurrentThread(vm, &env, NULL) != 0) {
				LOG_ERROR(LOG_TAG, "playbackTimestampCallback() jni: AttachCurrentThread() failed");
			}
		}
		else if (getEnvStat == JNI_EVERSION) {
			LOG_ERROR(LOG_TAG, "playbackTimestampCallback() jni: GetEnv() unsupported version");
		}
	}
	else {
		(*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
	}

	jmethodID methodPlaybackTimestampNotification = (*env)->GetMethodID(env, cls, "playbackUpdateTimestamp", "(J)V");
	(*env)->CallVoidMethod(env, obj, methodPlaybackTimestampNotification, (jlong) played);

	if (audiotrack_thread) {
		(*vm)->DetachCurrentThread(vm);
	}
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    engineInitialize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_engineInitialize(JNIEnv * env, jobject object, jboolean isTranscoder) {
	JavaVM * vm;
	(*env)->GetJavaVM(env, &vm);
	jobject obj = (*env)->NewGlobalRef(env, object);
	jclass cls  = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, obj));

	uint32_t channel_count;

	jlong engineJ = (*env)->GetLongField(env, obj, (*env)->GetFieldID(env, cls, "engineContext", "J"));
	engine_context_s * engine = id_to_ptr(engineJ);

	if (engine == NULL) {
		engine = memory_zero_alloc(sizeof(*engine));

		if (engine_new(engine, isTranscoder ? 1 : 0)) {
			LOG_ERROR(LOG_TAG, "engine_new() failure");
			goto engine_init_done_error;
		}

		if (engine_get_max_channel_count(engine, &channel_count)) {
			LOG_ERROR(LOG_TAG, "engine_get_max_channel_count() failure");
			goto engine_init_done_error;
		}

		if (engine_set_params(engine, SAMPLE_FORMAT_S16_NE, 44100, channel_count, STREAM_TYPE_MUSIC, 250)) {
			LOG_ERROR(LOG_TAG, "engine_set_params() failure");
			goto engine_init_done_error;
		}
	}

	engine_set_completion_callback(engine, &playbackEndedCallback);
	engine_set_timestamp_callback(engine, &playbackTimestampCallback);

	engine->vm = vm;
	engine->obj = obj;
	engine->cls = cls;

    (*env)->SetLongField(env, obj, (*env)->GetFieldID(env, cls, "engineContext", "J"), ptr_to_id(engine));

	return 0;
engine_init_done_error:
	engine_delete(engine);
	return -1;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    engineFinalize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_engineFinalize(JNIEnv * env, jobject object) {
    jclass cls = (*env)->GetObjectClass(env, object);
	jlong engineJ = (*env)->GetLongField(env, object, (*env)->GetFieldID(env, cls, "engineContext", "J"));
	engine_context_s * engine = id_to_ptr(engineJ);

    if (engine != NULL) {
        jobject obj = engine->obj;
        jclass cls  = engine->cls;

        (*env)->DeleteGlobalRef(env, obj);
        (*env)->DeleteGlobalRef(env, cls);

		engine_delete(engine);
	}

	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamInitialize
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamInitialize(JNIEnv * env, jobject object, jstring media_path) {
    jclass cls = (*env)->GetObjectClass(env, object);
	jlong engineJ = (*env)->GetLongField(env, object, (*env)->GetFieldID(env, cls, "engineContext", "J"));
	engine_context_s * engine = id_to_ptr(engineJ);

	engine_stream_context_s * stream = memory_alloc(sizeof *stream);

	if (engine != NULL && stream != NULL) {
		const char * stream_path = (*env)->GetStringUTFChars(env, media_path, NULL);
		if (engine_stream_new(engine, stream, stream_path) != ENGINE_OK) {
			memory_free(stream);
			return 0;
		}
		(*env)->ReleaseStringUTFChars(env, media_path, stream_path);
	}

	return ptr_to_id(stream);
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamFinalize
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamFinalize(JNIEnv * env, jobject object, jlong context) {
	engine_stream_context_s * stream = id_to_ptr(context);

	if (stream != NULL) {
		engine_stream_delete(stream);
		memory_free(stream);
	}

	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamPreload
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamPreload(JNIEnv * env, jobject object, jlong context) {
	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamStart
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamStart(JNIEnv * env, jobject object, jlong context) {
	engine_stream_context_s * stream = id_to_ptr(context);

	if (stream != NULL) {
		engine_stream_start(stream);
	}

	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamStop
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamStop(JNIEnv * env, jobject object, jlong context) {
	engine_stream_context_s * stream = id_to_ptr(context);

	if (stream != NULL) {
		engine_stream_stop(stream);
	}

	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamSetPosition
 * Signature: (JJ)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamSetPosition(JNIEnv * env, jobject object, jlong context, jlong position) {
	engine_stream_context_s * stream = id_to_ptr(context);

	if (stream != NULL) {
		engine_stream_set_position(stream, position);
	}

	return 0;
}

/*
  * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
  * Method:    streamGetPosition
  * Signature: (J)J
  */
 JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamGetPosition(JNIEnv * env, jobject object, jlong context) {
 	engine_stream_context_s * stream = id_to_ptr(context);
    int64_t position = 0;

 	if (stream != NULL) {
 		engine_stream_get_position(stream, &position);
 	}

 	return (jlong) position;
 }

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamGetDuration
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_opus_player_utils_jni_JniMediaLib_streamGetDuration(JNIEnv * env, jobject object, jlong context) {
	engine_stream_context_s * stream = id_to_ptr(context);
	int64_t result = 0;

	if (stream != NULL) {
		if (engine_stream_get_duration(stream, &result) != ENGINE_OK) {
			result = 0;
		}
	}

	return result;
}
