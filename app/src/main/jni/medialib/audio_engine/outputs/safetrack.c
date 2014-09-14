/*
 * safetrack.c
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

#include <audio_engine/outputs/safetrack.h>
#include <audio_engine/utils/memory.h>
#include <audio_engine/utils/log.h>
#include <jni.h>

#include <inttypes.h>

#define LOG_TAG "(jni).outputs.safetrack"

static JavaVMAttachArgs vmAttach;

static JNIEnv * get_env(engine_context_s * engine, int threaded) {
	JavaVM * vm = engine->vm;
	JNIEnv * env;

    vmAttach.version = JNI_VERSION_1_6;  /* must be JNI_VERSION_1_2 */
    vmAttach.name = "Safetrack-Thread";    /* the name of the thread as a modified UTF-8 string, or NULL */
    vmAttach.group = NULL; /* global ref of a ThreadGroup object, or NULL */

	if (!threaded) {
		(*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
	}
	else {
		int getEnvStat = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
		if (getEnvStat == JNI_EDETACHED) {
			if ((*vm)->AttachCurrentThread(vm, &env, &vmAttach) != 0) {
				LOG_ERROR(LOG_TAG, "jni: AttachCurrentThread() failed");
				return NULL;
			}
		} else if (getEnvStat == JNI_EVERSION) {
			LOG_ERROR(LOG_TAG, "jni: GetEnv() unsupported version");
			return NULL;
		}
	}

	return env;
}

static void release_env(engine_context_s * engine, int threaded) {
	if (threaded) {
		JavaVM * vm = engine->vm;
		(*vm)->DetachCurrentThread(vm);
	}
}

enum stream_type_e {
    STREAM_TYPE_VOICE_CALL = 0,
    STREAM_TYPE_SYSTEM = 1,
    STREAM_TYPE_RING = 2,
    STREAM_TYPE_MUSIC = 3,
    STREAM_TYPE_ALARM = 4,
    STREAM_TYPE_NOTIFICATION = 5,
    STREAM_TYPE_BLUETOOTH_SCO = 6,
    STREAM_TYPE_ENFORCED_AUDIBLE = 7,
    STREAM_TYPE_DTMF = 8,
    STREAM_TYPE_TTS = 9,
    STREAM_TYPE_FM = 10,
    STREAM_TYPE_MAX
};

/*
 * https://android.googlesource.com/platform/frameworks/base/+/android-2.2.3_r2.1/include/media/AudioSystem.h
 *
 */
enum channel_mapping {
	AUDIO_CHANNEL_OUT_FRONT_LEFT  = 0x1,
	AUDIO_CHANNEL_OUT_FRONT_RIGHT = 0x2,
	AUDIO_CHANNEL_OUT_MONO        = AUDIO_CHANNEL_OUT_FRONT_LEFT,
	AUDIO_CHANNEL_OUT_STEREO      = (AUDIO_CHANNEL_OUT_FRONT_LEFT | AUDIO_CHANNEL_OUT_FRONT_RIGHT)
};

enum channel_mapping_legacy {
	AUDIO_CHANNEL_OUT_FRONT_LEFT_LEGACY = 0x4,
	AUDIO_CHANNEL_OUT_FRONT_RIGHT_LEGACY = 0x8,
	AUDIO_CHANNEL_OUT_MONO_LEGACY = AUDIO_CHANNEL_OUT_FRONT_LEFT_LEGACY,
	AUDIO_CHANNEL_OUT_STEREO_LEGACY = (AUDIO_CHANNEL_OUT_FRONT_LEFT_LEGACY | AUDIO_CHANNEL_OUT_FRONT_RIGHT_LEGACY)
};

enum sample_format{
	AUDIO_FORMAT_PCM = 0,
	AUDIO_FORMAT_PCM_SUB_16_BIT = 0x1,
	AUDIO_FORMAT_PCM_16_BIT = (AUDIO_FORMAT_PCM | AUDIO_FORMAT_PCM_SUB_16_BIT),
};

enum audiotrack_playstate {
	PLAYSTATE_STOPPED = 1,
	PLAYSTATE_PAUSED = 2,
	PLAYSTATE_PLAYING = 3,
};

#define AUDIOTRACK_ENCODING_PCM16 2
#define AUDIOTRACK_MODE_STREAM 1

typedef struct {
	int stream_type;

	int has_valid_thread;
	pthread_t output_thread;
	pthread_mutex_t validity_lock;

	jclass audiotrack_class; /* TODO: move in a safetrack_output_context_s */
	jobject audiotrack_object;
	jint buffer_size;
	jshort * buffer;

	int64_t written_samples;
	int draining;
} safetrack_stream_context_s;

/* Prototype is used by feeding callback */
int safetrack_stream_stop(engine_stream_context_s * stream);
int safetrack_stream_flush(engine_stream_context_s * stream);
int safetrack_stream_get_position(engine_stream_context_s * stream, int64_t * position);

safetrack_stream_context_s * audiotrack_stream;

static void * output_thread(void * thread_arg) {
	engine_stream_context_s * stream = thread_arg;
	engine_context_s * engine = stream->engine;
	safetrack_stream_context_s * audiotrack_stream = stream->stream_output_specific;

	audiotrack_stream->buffer = memory_alloc(sizeof(jshort) * audiotrack_stream->buffer_size);

	JNIEnv * env = get_env(engine, 1);

	jmethodID getPlayStateMethod = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "getPlayState", "()I");
	jmethodID playMethod = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "play", "()V");
	jmethodID writeMethod = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "write", "([SII)I");
	jshortArray bytearray = (*env)->NewShortArray(env, audiotrack_stream->buffer_size);

	for (;;) {
		int playstate = (*env)->CallIntMethod(env, audiotrack_stream->audiotrack_object, getPlayStateMethod);
		if (playstate != PLAYSTATE_PLAYING) {
			LOG_INFO(LOG_TAG, "output_thread(): Updating playstate");
			(*env)->CallVoidMethod(env, audiotrack_stream->audiotrack_object, playMethod);
		}

		int request_size = audiotrack_stream->buffer_size / 40;
		int nb_samples = request_size / stream->engine->channel_count;
		int got = stream->data_callback(stream, audiotrack_stream->buffer, nb_samples);

		(*env)->SetShortArrayRegion(env, bytearray, 0, audiotrack_stream->buffer_size, audiotrack_stream->buffer);
		(*env)->CallIntMethod(env, audiotrack_stream->audiotrack_object, writeMethod, bytearray, 0, got * stream->engine->channel_count);

		if (!audiotrack_stream->has_valid_thread) {
			break;
		}

		if (got != nb_samples) {
			if (stream->decoder_terminated) {
				LOG_INFO(LOG_TAG, "output_thread(): Terminating (%i/%i)", got, nb_samples);
				safetrack_stream_flush(stream);
				break;
			}
			else {
				LOG_INFO(LOG_TAG, "output_thread(): Buffering issue (%i/%i)", got, nb_samples);
				// TODO: buffering issues...
			}

		}

        audiotrack_stream->written_samples = audiotrack_stream->written_samples + nb_samples;


	    int64_t played_ts = (audiotrack_stream->written_samples * (int64_t)1000) / stream->engine->sampling_rate;
	    played_ts = stream->last_timestamp + played_ts;

	    // prevent to much java callbacks.
	    if (played_ts - stream->last_timestamp_update > 100) {
	        stream->last_timestamp_update = played_ts;
		    stream->engine->timestamp_callback(stream, played_ts);
		}
	}

    memory_free(audiotrack_stream->buffer);

	/* sync */ pthread_mutex_lock(&audiotrack_stream->validity_lock);
	if (audiotrack_stream->has_valid_thread) {
		audiotrack_stream->has_valid_thread = 0;
	}
	/* sync */ pthread_mutex_unlock(&audiotrack_stream->validity_lock);

    jmethodID stopId = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "stop", "()V");
    (*env)->CallVoidMethod(env, audiotrack_stream->audiotrack_object, stopId);

    // TODO: optimize state callback (remove user_context arg)...
    stream->state_callback(stream, STREAM_STATE_STOPPED);

    // from this point, don't use anymore audiotrack_stream and stream.
	release_env(engine, 1);

	return 0;
}

int safetrack_new(engine_context_s * engine) {
	int channel_config = 0;
	int error_code = ENGINE_GENERIC_ERROR;

	audiotrack_stream = memory_zero_alloc(sizeof *audiotrack_stream);
	if (audiotrack_stream == NULL) {
		LOG_WARNING(LOG_TAG, "audiotrack_stream_new: Error allocating stream");
		goto audiotrack_stream_new_done;
	}

	if (engine->sample_format == SAMPLE_FORMAT_FLOAT32_LE || engine->sample_format == SAMPLE_FORMAT_FLOAT32_BE) {
		error_code = ENGINE_INVALID_FORMAT_ERROR;
		LOG_WARNING(LOG_TAG, "audiotrack_stream_new: ENGINE_INVALID_FORMAT_ERROR");
		goto audiotrack_stream_new_done;
	}

	audiotrack_stream->stream_type = STREAM_TYPE_MUSIC;

	channel_config = engine->channel_count == 2 ? AUDIO_CHANNEL_OUT_STEREO_LEGACY : AUDIO_CHANNEL_OUT_MONO_LEGACY;

	JNIEnv * env = get_env(engine, 0);
	jclass audiotrackClass = (*env)->FindClass(env, "android/media/AudioTrack");
	audiotrack_stream->audiotrack_class = (*env)->NewGlobalRef(env, audiotrackClass);

	/*
	 * Java equivalent :
	 * int buffer_size = AudioTrack.getMinBufferSize(sampling_rate, channel_config, stream_type);
	 */
	jmethodID getMinBufferSizeId = (*env)->GetStaticMethodID(env, audiotrack_stream->audiotrack_class, "getMinBufferSize", "(III)I");
	audiotrack_stream->buffer_size = (*env)->CallStaticIntMethod(env, audiotrack_stream->audiotrack_class, getMinBufferSizeId, engine->sampling_rate, channel_config, audiotrack_stream->stream_type);
	audiotrack_stream->buffer_size = audiotrack_stream->buffer_size * 10;

	/*
	 * Java equivalent :
	 * Object obj = new AudioTrack(stream_type, sampling_rate, channel_config, AudioTrack.ENCODING_PCM16, buffer_size, AudioTrack.MODE_STREAM);
	 */
	jmethodID ctor = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "<init>", "(IIIIII)V");

	jobject obj = (*env)->NewObject(env, audiotrack_stream->audiotrack_class, ctor,
				audiotrack_stream->stream_type,
				engine->sampling_rate,
				channel_config,
				AUDIOTRACK_ENCODING_PCM16,
				audiotrack_stream->buffer_size,
				AUDIOTRACK_MODE_STREAM);

	audiotrack_stream->audiotrack_object = (*env)->NewGlobalRef(env, obj);
	release_env(engine, 0);

	audiotrack_stream->has_valid_thread = 0;
	pthread_mutex_init(&audiotrack_stream->validity_lock, NULL);
	error_code = ENGINE_OK;

audiotrack_stream_new_done:
	if (error_code != ENGINE_OK) {
		if (audiotrack_stream != NULL) {
			memory_free(audiotrack_stream);
		}
		audiotrack_stream = NULL;
	}
	return error_code;
}

int safetrack_delete(engine_context_s * engine) {
	if (audiotrack_stream != NULL) {
		pthread_mutex_destroy(&audiotrack_stream->validity_lock);

        JNIEnv * env = get_env(engine, 0);
        jmethodID releaseId = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "release", "()V");
        (*env)->CallVoidMethod(env, audiotrack_stream->audiotrack_object, releaseId);

		(*env)->DeleteGlobalRef(env, audiotrack_stream->audiotrack_class);
		(*env)->DeleteGlobalRef(env, audiotrack_stream->audiotrack_object);
		release_env(engine, 0);

		memory_free(audiotrack_stream);
	}

	return ENGINE_OK;
}

char * safetrack_get_name(engine_context_s * engine) {
	return "Safe AudioTrack audio output";
}

int safetrack_get_max_channel_count(engine_context_s * engine, uint32_t * max_channels) {
	/*
		http://androidxref.com/4.2.2_r1/xref/frameworks/av/services/audioflinger/AudioFlinger.h#67
	*/
	*max_channels = 2;
	return ENGINE_OK;
}


int safetrack_stream_new(engine_context_s * engine, engine_stream_context_s * stream,
	engine_data_callback data_callback, engine_state_callback state_callback, void * user_context) {

	stream->data_callback = data_callback;
	stream->state_callback = state_callback;
	stream->user_context = user_context;

    stream->stream_output_specific = audiotrack_stream;
	stream->last_timestamp = 0;
	stream->last_timestamp_update = 0;

	return ENGINE_OK;
}

int safetrack_stream_delete(engine_stream_context_s * stream) {
	stream->stream_output_specific = NULL;
	return ENGINE_OK;
}

int safetrack_stream_start(engine_stream_context_s * stream) {
	safetrack_stream_context_s * audiotrack_stream = stream->stream_output_specific;
	int error_code = ENGINE_GENERIC_ERROR;


    stream->last_timestamp_update = 0;
	stream->state_callback(stream, STREAM_STATE_STARTED);

	/* sync */ pthread_mutex_lock(&audiotrack_stream->validity_lock);
	audiotrack_stream->has_valid_thread = 1;
	/* sync */ pthread_mutex_unlock(&audiotrack_stream->validity_lock);

	if (pthread_create(&audiotrack_stream->output_thread, NULL, output_thread, stream) == 0) {
		JNIEnv * env = get_env(stream->engine, 0);
		jmethodID playId = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "play", "()V");
		(*env)->CallVoidMethod(env, audiotrack_stream->audiotrack_object, playId);
		release_env(stream->engine, 0);
		error_code = ENGINE_OK;
	}

	return error_code;
}

int safetrack_stream_stop(engine_stream_context_s * stream) {
	safetrack_stream_context_s * audiotrack_stream = stream->stream_output_specific;
	int error_code = ENGINE_GENERIC_ERROR;

    stream->last_timestamp_update = 0;

	if (audiotrack_stream != NULL) {
		/* sync */ pthread_mutex_lock(&audiotrack_stream->validity_lock);
		if (audiotrack_stream->has_valid_thread) {
			audiotrack_stream->has_valid_thread = 0;
			/* sync */ pthread_mutex_unlock(&audiotrack_stream->validity_lock);

			if (pthread_join(audiotrack_stream->output_thread, NULL) == 0) {
				error_code = ENGINE_OK;
			}

			JNIEnv * env = get_env(stream->engine, 0);
			jmethodID stopId = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "stop", "()V");
			(*env)->CallVoidMethod(env, audiotrack_stream->audiotrack_object, stopId);
			release_env(stream->engine, 0);
			stream->state_callback(stream, STREAM_STATE_STOPPED);
		}
		else {
			/* sync */ pthread_mutex_unlock(&audiotrack_stream->validity_lock);
		}
	}

	return error_code;
}

int safetrack_stream_flush(engine_stream_context_s * stream) {
	safetrack_stream_context_s * audiotrack_stream = stream->stream_output_specific;

	if (audiotrack_stream != NULL) {
		JNIEnv * env = get_env(stream->engine, 0);
		jmethodID flushId = (*env)->GetMethodID(env, audiotrack_stream->audiotrack_class, "flush", "()V");
		(*env)->CallVoidMethod(env, audiotrack_stream->audiotrack_object, flushId);
		release_env(stream->engine, 0);
		audiotrack_stream->written_samples = 0;
		stream->last_timestamp_update = 0;
	}

	return ENGINE_OK;
}

static engine_output_s safetrack_output = {
	.create = safetrack_new,
	.destroy = safetrack_delete,
	.get_name = safetrack_get_name,
	.get_max_channel_count = safetrack_get_max_channel_count,
	.stream_create = safetrack_stream_new,
	.stream_destroy = safetrack_stream_delete,
	.stream_start = safetrack_stream_start,
	.stream_stop = safetrack_stream_stop,
	.stream_flush = safetrack_stream_flush
};

engine_output_s * safetrack_get_output() {
	return &safetrack_output;
}


