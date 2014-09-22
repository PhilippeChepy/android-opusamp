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
#include <jni.h>

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
typedef struct engine_processor_ engine_processor_s;

typedef long (* engine_data_callback)(engine_stream_context_s * stream, void * data_buffer, size_t data_length);
typedef void (* engine_state_callback)(engine_stream_context_s * stream, int stream_state);
typedef void (* engine_completion_callback)(engine_stream_context_s * stream);
typedef void (* engine_timestamp_callback)(engine_stream_context_s * stream, int64_t played);

typedef struct {
	int (* create)(engine_context_s * engine);
	int (* destroy)(engine_context_s * engine);
	char * (* get_name)(engine_context_s * engine);
	int (* get_max_channel_count)(engine_context_s * engine, uint32_t * max_channels);

	int (* stream_create)(engine_context_s * engine, engine_stream_context_s * stream, engine_data_callback data_callback, engine_state_callback state_callback, void * user_context);
	int (* stream_destroy)(engine_stream_context_s * stream);
	int (* stream_start)(engine_stream_context_s * stream);
	int (* stream_stop)(engine_stream_context_s * stream);
	int (* stream_flush)(engine_stream_context_s * stream);

    void * context;
} engine_output_s;

typedef struct {
	int (* create)(engine_context_s * engine);
	int (* destroy)(engine_context_s * engine);
	char * (* get_name)(engine_context_s * engine);
	int (* get_max_channel_count)(engine_context_s * engine, uint32_t * max_channels);

	int (* stream_create)(engine_context_s * engine, engine_stream_context_s * stream, const char * media_path, engine_data_callback data_callback, engine_state_callback state_callback, void * user_context);
	int (* stream_destroy)(engine_stream_context_s * stream);
	int (* stream_start)(engine_stream_context_s * stream);
	int (* stream_stop)(engine_stream_context_s * stream);

	int (* stream_get_duration)(engine_stream_context_s * stream, int64_t * duration);
	int (* stream_set_position)(engine_stream_context_s * stream, int64_t position);

    void * context;
} engine_input_s;

struct engine_processor_ {
    int (* create)(engine_processor_s * processor);
    int (* destroy)(engine_processor_s * processor);
    char * (* get_name)(engine_context_s * engine);

    int (* set_property)(engine_processor_s * processor, int property, void * value);
    int (* get_property)(engine_processor_s * processor, int property, void * value);
    int (* apply_properties)(engine_processor_s * processor);
    int (* process)(engine_processor_s * processor, void * data_buffer, size_t data_length);

    engine_context_s * engine;
    int enabled;
    void * context;
};

struct engine_context_ {
	engine_output_s * output;
	engine_input_s * input;

	engine_processor_s ** processor_list;
	size_t processor_count;

    engine_timestamp_callback timestamp_callback;
    engine_completion_callback completion_callback;

	int sample_format;
	int sampling_rate;
	int channel_count;

	int default_audio_buffer_size; /* capacity of engine_stream_context_.audio_buffer */
	int decoder_thread_sleep_threshold;
	int decoder_thread_wake_threshold;

	JavaVM * vm;
	jobject obj;
	jclass cls;
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

	engine_data_callback data_callback;
	engine_state_callback state_callback;
	void * user_context;
};

int engine_new(engine_context_s * engine, int sample_format, int sampling_rate, int channel_count);
int engine_delete(engine_context_s * engine);
int engine_get_max_channel_count(engine_context_s * engine, uint32_t * max_channels);

int engine_dsp_init(engine_context_s * engine);
int engine_dsp_is_enabled(engine_context_s * engine, int dsp_id);
int engine_dsp_set_enabled(engine_context_s * engine, int dsp_id, int enabled);
int engine_dsp_set_property(engine_context_s * engine, int dsp_id, int property, void * value);
int engine_dsp_get_property(engine_context_s * engine, int dsp_id, int property, void * value);

int engine_stream_new(engine_context_s * engine, engine_stream_context_s * stream, const char * media_path);
int engine_stream_delete(engine_stream_context_s * stream);
int engine_stream_preload(engine_stream_context_s * stream);
int engine_stream_start(engine_stream_context_s * stream);
int engine_stream_stop(engine_stream_context_s * stream);
int engine_stream_get_position(engine_stream_context_s * stream, int64_t * position);
int engine_stream_set_position(engine_stream_context_s * stream, int64_t position);

int engine_stream_get_duration(engine_stream_context_s * stream, int64_t * duration);

int engine_set_completion_callback(engine_context_s * engine, engine_completion_callback callback);
int engine_set_timestamp_callback(engine_context_s * engine, engine_timestamp_callback callback);

#ifdef __cplusplus
}
#endif

#endif /* H_ENGINE */
