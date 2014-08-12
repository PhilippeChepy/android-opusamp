/*
 * transcoder.c
 *
 * Copyright (c) 2014, Philippe Chepy
 * All rights reserved.
 *
 * This software is the confidential and proprietary information
 * of Philippe Chepy.
 * You shall not disclose such Confidential Information.
 *
 * http://www.chepy.eu
 */

#include <audio_engine/outputs/transcoder.h>
#include <audio_engine/utils/memory.h>
#include <audio_engine/utils/log.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
//#include <libavfilter/avfiltergraph.h>
#include <libavfilter/avcodec.h>
//#include <libavfilter/buffersink.h>
//#include <libavfilter/buffersrc.h>
//#include <libavutil/opt.h>
//#include <libavutil/pixdesc.h>

#include <inttypes.h>

#define LOG_TAG "(jni).outputs.transcoder"


static JNIEnv * get_env(engine_stream_context_s * stream_context, int threaded) {
	JavaVM * vm = stream_context->engine->vm;
	JNIEnv * env;

    //vmAttach.version = JNI_VERSION_1_6;  /* must be JNI_VERSION_1_2 */
    //vmAttach.name = "Transcode-Thread";    /* the name of the thread as a modified UTF-8 string, or NULL */
    //vmAttach.group = NULL; /* global ref of a ThreadGroup object, or NULL */

	if (!threaded) {
		(*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
	}
	else {
		int getEnvStat = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
		if (getEnvStat == JNI_EDETACHED) {
			if ((*vm)->AttachCurrentThread(vm, &env, NULL /*&vmAttach*/) != 0) {
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

static void release_env(engine_stream_context_s * stream_context, int threaded) {
	if (threaded) {
		JavaVM * vm = stream_context->engine->vm;
		(*vm)->DetachCurrentThread(vm);
	}
}


typedef struct {
    int stream_type;
	engine_data_callback data_callback;
	engine_state_callback state_callback;

	int has_valid_thread;
	pthread_t output_thread;
	pthread_mutex_t validity_lock;

	size_t buffer_size;
	int16_t * buffer;

	void * user_context;
	int64_t written_samples;
} transcoder_stream_context_s;

int transcoder_stream_stop(engine_stream_context_s * stream_context);
int transcoder_stream_flush(engine_stream_context_s * stream_context);
int transcoder_stream_get_position(engine_stream_context_s * stream_context, int64_t * position);

static void * output_thread(void * thread_arg) {
	engine_stream_context_s * stream_context = thread_arg;

	transcoder_stream_context_s * transcoder_stream = stream_context->stream_output_specific;
	transcoder_stream->buffer = memory_alloc(sizeof(uint16_t) * transcoder_stream->buffer_size);

    get_env(stream_context, 1);

	for (;;) {
		int request_size = transcoder_stream->buffer_size / 40;
		int nb_samples = request_size / stream_context->engine->param_channel_count;
		int got = transcoder_stream->data_callback(stream_context, transcoder_stream->user_context, transcoder_stream->buffer, nb_samples);

        // TODO: write transcoder_stream->buffer[0, got * stream_context->engine->param_channel_count]

		if (!transcoder_stream->has_valid_thread) {
			break;
		}

		if (got != nb_samples) {
			if (stream_context->decoder_terminated) {
				LOG_INFO(LOG_TAG, "output_thread(): Terminating (%i/%i)", got, nb_samples);
				transcoder_stream_flush(stream_context);
				transcoder_stream->state_callback(stream_context, transcoder_stream->user_context, STREAM_STATE_STOPPED);
				break;
			}
		}

        transcoder_stream->written_samples = transcoder_stream->written_samples + nb_samples;

	    int64_t played_ts = (transcoder_stream->written_samples * (int64_t)1000) / stream_context->engine->param_sampling_rate;
	    played_ts = stream_context->last_timestamp + played_ts;

	    // prevent to much java callbacks.
	    if (played_ts - stream_context->last_timestamp_update > 100) {
	        stream_context->last_timestamp_update = played_ts;
		    stream_context->engine->timestamp_callback(stream_context, played_ts);
		}
	}

	/* sync */ pthread_mutex_lock(&transcoder_stream->validity_lock);
	if (transcoder_stream->has_valid_thread) {
		transcoder_stream->has_valid_thread = 0;
	}
	/* sync */ pthread_mutex_unlock(&transcoder_stream->validity_lock);

    release_env(stream_context, 1);
	memory_free(transcoder_stream->buffer);

	return 0;
}

int transcoder_new(engine_context_s * engine_context) {
	return ENGINE_OK;
}

int transcoder_delete(engine_context_s * engine_context) {
	return ENGINE_OK;
}

int transcoder_get_name(engine_context_s * engine_context, char ** output_name) {
	*output_name = "File audio output";
	return ENGINE_OK;
}

int transcoder_get_max_channel_count(engine_context_s * engine_context, uint32_t * max_channels) {
	*max_channels = 2;
	return ENGINE_OK;
}

int transcoder_stream_new(engine_context_s * engine_context, engine_stream_context_s * stream_context, int stream_type, int stream_latency, engine_data_callback data_callback, engine_state_callback state_callback, void * user_context) {
	int error_code = ENGINE_GENERIC_ERROR;

	transcoder_stream_context_s * transcoder_stream = memory_zero_alloc(sizeof *transcoder_stream);

	if (transcoder_stream == NULL) {
		LOG_WARNING(LOG_TAG, "transcoder_stream_new: Error allocating stream");
		goto transcoder_stream_new_done;
	}

	if (engine_context->param_sample_format == SAMPLE_FORMAT_FLOAT32_LE || engine_context->param_sample_format == SAMPLE_FORMAT_FLOAT32_BE) {
		error_code = ENGINE_INVALID_FORMAT_ERROR;
		LOG_WARNING(LOG_TAG, "transcoder_stream_new: ENGINE_INVALID_FORMAT_ERROR");
		goto transcoder_stream_new_done;
	}

	transcoder_stream->data_callback = data_callback;
	transcoder_stream->state_callback = state_callback;
	transcoder_stream->user_context = user_context;
	transcoder_stream->stream_type = stream_type;
	transcoder_stream->buffer_size = 65536; // 64kB
	transcoder_stream->has_valid_thread = 0;
	pthread_mutex_init(&transcoder_stream->validity_lock, NULL);

	stream_context->stream_output_specific = transcoder_stream;
	stream_context->last_timestamp = 0;
	stream_context->last_timestamp_update = 0;

	error_code = ENGINE_OK;

transcoder_stream_new_done:
	if (error_code != ENGINE_OK) {
		if (transcoder_stream != NULL) {
			memory_free(transcoder_stream);
		}
		stream_context->stream_output_specific = NULL;
	}

	return error_code;
}

int transcoder_stream_delete(engine_stream_context_s * stream_context) {
	transcoder_stream_context_s * transcoder_stream = stream_context->stream_output_specific;

	if (transcoder_stream != NULL) {
		pthread_mutex_destroy(&transcoder_stream->validity_lock);
        memory_free(transcoder_stream->buffer);
		memory_free(transcoder_stream);

		stream_context->stream_output_specific = NULL;
	}

	return ENGINE_OK;
}

int transcoder_stream_start(engine_stream_context_s * stream_context) {
	transcoder_stream_context_s * transcoder_stream = stream_context->stream_output_specific;
	int error_code = ENGINE_GENERIC_ERROR;

    stream_context->last_timestamp_update = 0;
	transcoder_stream->state_callback(stream_context, transcoder_stream->user_context, STREAM_STATE_STARTED);

	/* sync */ pthread_mutex_lock(&transcoder_stream->validity_lock);
	transcoder_stream->has_valid_thread = 1;
	/* sync */ pthread_mutex_unlock(&transcoder_stream->validity_lock);

	if (pthread_create(&transcoder_stream->output_thread, NULL, output_thread, stream_context) == 0) {
		error_code = ENGINE_OK;
	}

	return error_code;
}

int transcoder_stream_stop(engine_stream_context_s * stream_context) {
	transcoder_stream_context_s * transcoder_stream = stream_context->stream_output_specific;
	int error_code = ENGINE_GENERIC_ERROR;

    stream_context->last_timestamp_update = 0;

	if (transcoder_stream != NULL) {
		/* sync */ pthread_mutex_lock(&transcoder_stream->validity_lock);
		if (transcoder_stream->has_valid_thread) {
			transcoder_stream->has_valid_thread = 0;
			/* sync */ pthread_mutex_unlock(&transcoder_stream->validity_lock);

			if (pthread_join(transcoder_stream->output_thread, NULL) == 0) {
				error_code = ENGINE_OK;
			}

			transcoder_stream->state_callback(stream_context, transcoder_stream->user_context, STREAM_STATE_STOPPED);
		}
		else {
			/* sync */ pthread_mutex_unlock(&transcoder_stream->validity_lock);
		}
	}

	return error_code;
}

int transcoder_stream_flush(engine_stream_context_s * stream_context) {
	transcoder_stream_context_s * transcoder_stream = stream_context->stream_output_specific;

	if (transcoder_stream != NULL) {
		transcoder_stream->written_samples = 0;
		stream_context->last_timestamp_update = 0;
	}

	return ENGINE_OK;
}

static engine_output_s const transcoder_output;

engine_output_s const * transcoder_get_output() {
	return &transcoder_output;
}

static engine_output_s const transcoder_output = {
	.engine_new = transcoder_new,
	.engine_delete = transcoder_delete,
	.engine_get_name = transcoder_get_name,
	.engine_get_max_channel_count = transcoder_get_max_channel_count,
	.engine_stream_new = transcoder_stream_new,
	.engine_stream_delete = transcoder_stream_delete,
	.engine_stream_start = transcoder_stream_start,
	.engine_stream_stop = transcoder_stream_stop,
	.engine_stream_flush = transcoder_stream_flush
};
