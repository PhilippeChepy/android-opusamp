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
#include <audio_engine/outputs/safetrack.h> /* this output needs a VM ref */
#include <audio_engine/utils/log.h>
#include <audio_engine/utils/memory.h>

#include <inttypes.h>

#define LOG_TAG "AudioEnginePlayback-JNI"

static int g_engine_is_initialized = 0;
static engine_context_s g_engine;

static JavaVM * g_vm;
static jclass g_cl;
static jobject g_obj;

static long ptr_to_id(void * ptr) {
	return (long) ptr;
}

static void * id_to_ptr(long id) {
	return (void *) id;
}

void playbackEndedCallback(engine_stream_context_s * stream) {
	JNIEnv * env;
	int getEnvStat = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
	if (getEnvStat == JNI_EDETACHED) {
		if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
			LOG_ERROR(LOG_TAG, "playbackEndedCallback() jni: AttachCurrentThread() failed");
		}
	} else if (getEnvStat == JNI_EVERSION) {
		LOG_ERROR(LOG_TAG, "playbackEndedCallback() jni: GetEnv() unsupported version");
	}

	jmethodID methodPlaybackEndNotification = (*env)->GetMethodID(env, g_cl, "playbackEndNotification", "()V");
	(*env)->CallVoidMethod(env, g_obj, methodPlaybackEndNotification);

	(*g_vm)->DetachCurrentThread(g_vm);
}

void playbackTimestampCallback(engine_stream_context_s * stream, int64_t played) {
	JNIEnv * env;
	int audiotrack_thread = 0;

	if (stream->engine->output->engine_get_name != safetrack_get_output()->engine_get_name) {
		audiotrack_thread = 1;
	}

	if (audiotrack_thread) {
		int getEnvStat = (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
		if (getEnvStat == JNI_EDETACHED) {
			if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != 0) {
				LOG_ERROR(LOG_TAG, "playbackTimestampCallback() jni: AttachCurrentThread() failed");
			}
		}
		else if (getEnvStat == JNI_EVERSION) {
			LOG_ERROR(LOG_TAG, "playbackTimestampCallback() jni: GetEnv() unsupported version");
		}
	}
	else {
		(*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6);
	}

	jmethodID methodPlaybackTimestampNotification = (*env)->GetMethodID(env, g_cl, "playbackUpdateTimestamp", "(J)V");
	(*env)->CallVoidMethod(env, g_obj, methodPlaybackTimestampNotification, (jlong) played);

	if (audiotrack_thread) {
		(*g_vm)->DetachCurrentThread(g_vm);
	}
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    engineInitialize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_engineInitialize(JNIEnv * env, jobject object) {
	(*env)->GetJavaVM(env, &g_vm);
	g_obj = (*env)->NewGlobalRef(env, object);
	g_cl  = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, g_obj));

	safetrack_set_vm(g_vm);

	uint32_t channel_count;

	if (g_engine_is_initialized == 0) {
		if (engine_new(&g_engine)) {
			LOG_ERROR(LOG_TAG, "engine_new() failure");
			goto engine_init_done_error;
		}

		g_engine_is_initialized = 1;
		if (engine_get_max_channel_count(&g_engine, &channel_count)) {
			LOG_ERROR(LOG_TAG, "engine_get_max_channel_count() failure");
			goto engine_init_done_error;
		}

		if (engine_set_params(&g_engine, SAMPLE_FORMAT_S16_NE, 44100, channel_count, STREAM_TYPE_MUSIC, 250)) {
			LOG_ERROR(LOG_TAG, "engine_set_params() failure");
			goto engine_init_done_error;
		}
	}

	engine_set_completion_callback(&g_engine, &playbackEndedCallback);
	engine_set_timestamp_callback(&g_engine, &playbackTimestampCallback);

	return 0;
engine_init_done_error:
	engine_delete(&g_engine);
	return -1;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    engineFinalize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_engineFinalize(JNIEnv * env, jobject object) {
	(*env)->DeleteGlobalRef(env, g_obj);
	(*env)->DeleteGlobalRef(env, g_cl);

	if (g_engine_is_initialized != 0) {
		engine_delete(&g_engine);
		g_engine_is_initialized = 0;
	}

	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamInitialize
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamInitialize(JNIEnv * env, jobject object, jstring media_path) {
	engine_stream_context_s * stream = memory_alloc(sizeof *stream);

	if (stream != NULL) {
		const char * stream_path = (*env)->GetStringUTFChars(env, media_path, NULL);
		if (engine_stream_new(&g_engine, stream, stream_path) != ENGINE_OK) {
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
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamFinalize(JNIEnv * env, jobject object, jlong context) {
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
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamPreload(JNIEnv * env, jobject object, jlong context) {
	return 0;
}

/*
 * Class:     eu_chepy_audiokit_utils_jni_JniMediaLib
 * Method:    streamStart
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamStart(JNIEnv * env, jobject object, jlong context) {
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
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamStop(JNIEnv * env, jobject object, jlong context) {
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
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamSetPosition(JNIEnv * env, jobject object, jlong context, jlong position) {
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
 JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamGetPosition(JNIEnv * env, jobject object, jlong context) {
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
JNIEXPORT jlong JNICALL Java_eu_chepy_audiokit_utils_jni_JniMediaLib_streamGetDuration(JNIEnv * env, jobject object, jlong context) {
	engine_stream_context_s * stream = id_to_ptr(context);
	int64_t result = 0;

	if (stream != NULL) {
		if (engine_stream_get_duration(stream, &result) != ENGINE_OK) {
			result = 0;
		}
	}

	return result;
}
