/*
 * engine.h
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

#ifndef H_ENGINE
#define H_ENGINE

#ifdef __cplusplus
extern "C" {
#endif

#include <audio_engine/utils/stdcompat.h>
#include <audio_engine/utils/circular_buffer.h>
#include <pthread.h>

enum sample_format_e {
	SAMPLE_FORMAT_S16_LE,
	SAMPLE_FORMAT_S16_BE,
	SAMPLE_FORMAT_FLOAT32_LE,
	SAMPLE_FORMAT_FLOAT32_BE,
#if defined(__BIG_ENDIAN__)
	SAMPLE_FORMAT_S16_NE = SAMPLE_FORMAT_S16_BE,
	SAMPLE_FORMAT_FLOAT32_NE = SAMPLE_FORMAT_FLOAT32_BE
#else
	SAMPLE_FORMAT_S16_NE = SAMPLE_FORMAT_S16_LE,
	SAMPLE_FORMAT_FLOAT32_NE = SAMPLE_FORMAT_FLOAT32_LE
#endif
};

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

enum stream_state_e {
	STREAM_STATE_STARTED,
	STREAM_STATE_STOPPED,
	STREAM_STATE_TERMINATED,
	STREAM_STATE_ERROR
};

enum engine_result_code_e {
	ENGINE_TERMINATED = 1,
	ENGINE_OK = 0,
	ENGINE_GENERIC_ERROR = -1,
	ENGINE_INVALID_FORMAT_ERROR = -2,
	ENGINE_INVALID_PARAMETER_ERROR = -3,
	ENGINE_ALLOC_ERROR = -4,
	ENGINE_FILE_ACCESS_ERROR = -5,
};

typedef struct engine_context_ engine_context_s;

typedef struct engine_stream_context_ engine_stream_context_s;

typedef long (* engine_data_callback)(engine_stream_context_s * stream_context, void * user_context, void * data_buffer, size_t data_length);

typedef void (* engine_state_callback)(engine_stream_context_s * stream_context, void * user_context, int stream_state);

typedef void (* engine_completion_callback)(engine_stream_context_s * stream);

typedef void (* engine_timestamp_callback)(engine_stream_context_s * stream, int64_t played);

typedef struct {
	int (* engine_new)(engine_context_s * engine_context);
	int (* engine_delete)(engine_context_s * engine_context);
	int (* engine_get_name)(engine_context_s * engine_context, char ** output_name);
	int (* engine_get_max_channel_count)(engine_context_s * engine_context, uint32_t * max_channels);

	int (* engine_stream_new)(engine_context_s * engine_context, engine_stream_context_s * stream_context,
			int stream_type, int stream_latency, engine_data_callback data_callback, engine_state_callback state_callback, void * user_context);
	int (* engine_stream_delete)(engine_stream_context_s * stream_context);
	int (* engine_stream_start)(engine_stream_context_s * stream_context);
	int (* engine_stream_stop)(engine_stream_context_s * stream_context);
	int (* engine_stream_flush)(engine_stream_context_s * stream_context);
} engine_output_s;

typedef struct {
	int (* engine_new)(engine_context_s * engine_context);
	int (* engine_delete)(engine_context_s * engine_context);
	int (* engine_get_name)(engine_context_s * engine_context, char ** input_name);
	int (* engine_get_max_channel_count)(engine_context_s * engine_context, uint32_t * max_channels);

	int (* engine_stream_new)(engine_context_s * engine_context, engine_stream_context_s * stream_context,
			const char * media_path, engine_data_callback data_callback, engine_state_callback state_callback, void * user_context);
	int (* engine_stream_delete)(engine_stream_context_s * stream_context);
	int (* engine_stream_start)(engine_stream_context_s * stream_context);
	int (* engine_stream_stop)(engine_stream_context_s * stream_context);

	int (* engine_stream_get_duration)(engine_stream_context_s * stream_context, int64_t * duration);
	int (* engine_stream_set_position)(engine_stream_context_s * stream_context, int64_t position);
} engine_input_s;

struct engine_context_ {
	engine_output_s const * output;
	engine_input_s const * input;

	engine_completion_callback completion_callback;
	engine_timestamp_callback timestamp_callback;

	int param_sample_format;
	int param_sampling_rate;
	int param_channel_count;
	int param_stream_latency;
	int param_stream_type;

	int param_buffer_size; /* capacity of engine_stream_context_.audio_buffer */
	int param_sleep_decoder_buffer_threshold;
	int param_wake_decoder_buffer_threshold;

	void * engine_output_specific;
	void * engine_input_specific;
};

struct engine_stream_context_ {
	engine_context_s * engine;

	circular_buffer_s audio_buffer;

	pthread_mutex_t buffer_lock;
	pthread_cond_t buffer_full_cond;
	int decoder_is_waiting;
	int decoder_is_stopping;
	int decoder_terminated;

	void * stream_output_specific;
	void * stream_input_specific;

	pthread_mutex_t state_lock;

	int input_stream_state;
	int output_stream_state;

	int64_t last_timestamp;
	int64_t last_timestamp_update;
};

int engine_new(engine_context_s * engine_context);
int engine_delete(engine_context_s * engine_context);
int engine_set_params(engine_context_s * engine_context, int sample_format, int sampling_rate, int channel_count, int stream_type, int stream_latency);
int engine_get_output_name(engine_context_s * engine_context, char ** output_name);
int engine_get_input_name(engine_context_s * engine_context, char ** input_name);
int engine_get_max_channel_count(engine_context_s * engine_context, uint32_t * max_channels);

int engine_stream_new(engine_context_s * engine_context, engine_stream_context_s * stream_context, const char * media_path);
int engine_stream_delete(engine_stream_context_s * stream_context);
int engine_stream_start(engine_stream_context_s * stream_context);
int engine_stream_stop(engine_stream_context_s * stream_context);
int engine_stream_get_position(engine_stream_context_s * stream_context, int64_t * position);
int engine_stream_set_position(engine_stream_context_s * stream_context, int64_t position);

int engine_stream_get_duration(engine_stream_context_s * stream_context, int64_t * duration);

int engine_set_completion_callback(engine_context_s * engine_context, engine_completion_callback callback);
int engine_set_timestamp_callback(engine_context_s * engine_context, engine_timestamp_callback callback);

#ifdef __cplusplus
}
#endif

#endif /* H_ENGINE */
