/*
 * engine.c
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

#include <audio_engine/engine.h>
#include <audio_engine/outputs/safetrack.h>
#include <audio_engine/inputs/ffinput.h>
#include <audio_engine/utils/log.h>

#include <fcntl.h>
#include <unistd.h>
#include <inttypes.h>

#define LOG_TAG "(jni).engine"

long input_data_callback(engine_stream_context_s * stream, void * user_context, void * data_buffer, size_t data_length) {
	size_t total_write_length = 0;

	do {
		if (stream->decoder_is_stopping) {
			break;
		}

		/* sync */ pthread_mutex_lock(&stream->buffer_lock);
		if (stream->audio_buffer.used >= stream->engine->param_sleep_decoder_buffer_threshold) {
			/* sync */ stream->decoder_is_waiting = 1;
			if (!stream->decoder_is_stopping) {
				/* sync */ pthread_cond_wait(&stream->buffer_full_cond, &stream->buffer_lock);
			}
		}
		size_t write_length = data_length - total_write_length;
		circular_buffer_write(&stream->audio_buffer, &((char *)data_buffer)[total_write_length], &write_length);
		total_write_length += write_length;
		/* sync */ pthread_mutex_unlock(&stream->buffer_lock);
	} while (total_write_length < data_length);

	return (long)total_write_length;
}

void input_state_callback(engine_stream_context_s * stream, void * user_context, int stream_state) {
	LOG_INFO(LOG_TAG, "input_state_callback: %i", stream_state);

	pthread_mutex_lock(&stream->state_lock);
	stream->input_stream_state = stream_state;

	if ((stream->input_stream_state == STREAM_STATE_TERMINATED) && (stream->output_stream_state != STREAM_STATE_STARTED)) {
		stream->input_stream_state = STREAM_STATE_STOPPED;

		LOG_INFO(LOG_TAG, "input_state_callback: playback terminated");
		stream->engine->completion_callback(stream);
	}
	pthread_mutex_unlock(&stream->state_lock);
}

void output_state_callback(engine_stream_context_s * stream, void * user_context, int stream_state) {
	LOG_INFO(LOG_TAG, "output_state_callback: %i", stream_state);

	pthread_mutex_lock(&stream->state_lock);
	stream->output_stream_state = stream_state;

	if ((stream->input_stream_state == STREAM_STATE_TERMINATED) && (stream->output_stream_state != STREAM_STATE_STARTED)) {
		stream->input_stream_state = STREAM_STATE_STOPPED;

		LOG_INFO(LOG_TAG, "output_state_callback: playback terminated");
		stream->engine->completion_callback(stream);
	}
	pthread_mutex_unlock(&stream->state_lock);
}

long output_data_callback(engine_stream_context_s * stream, void * user_context, void * data_buffer, size_t nb_samples) {
	size_t total_read_length = 0;
	size_t data_length = nb_samples * stream->engine->param_channel_count;

	do {
		/* sync */ pthread_mutex_lock(&stream->buffer_lock);

		/* samples are s16 integers */
		size_t read_length = data_length * sizeof(int16_t) - total_read_length;

		circular_buffer_read(&stream->audio_buffer, &((int16_t *)data_buffer)[total_read_length], &read_length);
		total_read_length += read_length;

		/* waking up decoder thread if buffer is at  "param_wake_decoder_buffer_threshold" capacity */
		if (stream->audio_buffer.used < stream->engine->param_wake_decoder_buffer_threshold &&
				stream->decoder_is_waiting) {
			/* sync */ stream->decoder_is_waiting = 0;
			/* sync */ pthread_cond_broadcast(&stream->buffer_full_cond);
		}
		/* sync */ pthread_mutex_unlock(&stream->buffer_lock);

		if (stream->input_stream_state != ENGINE_OK) {
			break;
		}
	} while (total_read_length < data_length);

	return (total_read_length / sizeof(int16_t)) / stream->engine->param_channel_count;
}

int engine_new(engine_context_s * engine) {
	int engine_error_code = ENGINE_GENERIC_ERROR;

	engine->input = ffinput_get_input();
	engine_error_code = engine->input->create(engine);
	if (engine_error_code != ENGINE_OK) {
		LOG_INFO(LOG_TAG, "engine_new: input ffinput is returning an error.");
		goto engine_new_done;
	}
	else {
		LOG_INFO(LOG_TAG, "engine_new: using ffinput input.");
	}

/*	output = opensl_get_output(); / * >= API9 (gingerbread) + */
    engine->output = safetrack_get_output(); /* otherwise (low performences) */

	engine_error_code = engine->output->create(engine);

	if (engine_error_code == ENGINE_OK) {
	    LOG_INFO(LOG_TAG, "engine_new: using '%s' output.", engine->output->get_name(engine));
		engine->completion_callback = NULL;
		engine->param_buffer_size = 100 * 1024;
		engine->param_sleep_decoder_buffer_threshold = 100 * 1024;
		engine->param_wake_decoder_buffer_threshold = 40  * 1024;
	}
    else {
        LOG_INFO(LOG_TAG, "engine_new: output '%s' is not available.", engine->output->get_name(engine));
    }

engine_new_done:
	return engine_error_code;
}

int engine_delete(engine_context_s * engine) {
	LOG_INFO(LOG_TAG, "engine_delete: deleting engine.");
	if (engine == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	return engine->output->destroy(engine);
}

int engine_set_params(engine_context_s * engine, int sample_format, int sampling_rate, int channel_count, int stream_type, int stream_latency) {
	int engine_error_code = ENGINE_GENERIC_ERROR;

	if (sampling_rate < 1 || sampling_rate > 192000) {
		engine_error_code = ENGINE_INVALID_FORMAT_ERROR;
		LOG_ERROR(LOG_TAG, "engine_stream_new: invalid sampling rate (%i)", sampling_rate);
		goto engine_set_params_done;
	}

	if (channel_count < 1 || channel_count > 8) {
		engine_error_code = ENGINE_INVALID_FORMAT_ERROR;
		LOG_ERROR(LOG_TAG, "engine_stream_new: invalid channel_count (%i)", channel_count);
		goto engine_set_params_done;
	}

	switch (sample_format) {
	case SAMPLE_FORMAT_S16_LE:
	case SAMPLE_FORMAT_S16_BE:
	case SAMPLE_FORMAT_FLOAT32_LE:
	case SAMPLE_FORMAT_FLOAT32_BE:
		break;
	default:
		LOG_ERROR(LOG_TAG, "engine_stream_new: invalid sample format");
		engine_error_code = ENGINE_INVALID_FORMAT_ERROR;
		goto engine_set_params_done;
	}

	if (stream_latency < 1 || stream_latency > 2000) {
		engine_error_code = ENGINE_INVALID_PARAMETER_ERROR;
		LOG_ERROR(LOG_TAG, "engine_stream_new: invalid latency (%i)", stream_latency);
		goto engine_set_params_done;
	}

	engine->param_sample_format = sample_format;
	engine->param_sampling_rate = sampling_rate;
	engine->param_channel_count = channel_count;
	engine->param_stream_type = stream_type;
	engine->param_stream_latency = stream_latency;

	engine_error_code = ENGINE_OK;

engine_set_params_done:
	return engine_error_code;
}

int engine_set_completion_callback(engine_context_s * engine, engine_completion_callback callback) {
	engine->completion_callback = callback;
	return ENGINE_OK;
}

int engine_set_timestamp_callback(engine_context_s * engine, engine_timestamp_callback callback) {
	engine->output->timestamp_callback = callback;
	return ENGINE_OK;
}

int engine_get_max_channel_count(engine_context_s * engine, uint32_t * max_channels) {
	uint32_t output_max;
	uint32_t input_max;
	int error_code;

	if (engine == NULL || max_channels == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	error_code = engine->output->get_max_channel_count(engine, &output_max);
	if (error_code != ENGINE_OK) {
		return error_code;
	}
	error_code = engine->input->get_max_channel_count(engine, &input_max);
	if (error_code != ENGINE_OK) {
		return error_code;
	}

	*max_channels = input_max < output_max ? input_max : output_max;

	return ENGINE_OK;
}

int engine_stream_new(engine_context_s * engine, engine_stream_context_s * stream, const char * media_path) {
	int engine_error_code = ENGINE_GENERIC_ERROR;

	LOG_INFO(LOG_TAG, "engine_stream_new: creating a new stream.");

	if (engine == NULL || stream == NULL) {
		engine_error_code = ENGINE_INVALID_PARAMETER_ERROR;
		LOG_ERROR(LOG_TAG, "engine_stream_new: ENGINE_INVALID_PARAMETER_ERROR");
		goto stream_new_done;
	}

	if (circular_buffer_new(&stream->audio_buffer, engine->param_buffer_size) != CIRCULAR_BUFFER_OK) {
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to allocate circular buffer");
		goto stream_new_done;
	}
	stream->engine = engine;

	if (pthread_mutex_init(&stream->buffer_lock, NULL) != 0) { /* XXX */
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to initialize mutex");
		goto stream_new_done;
	}

	if (pthread_mutex_init(&stream->state_lock, NULL) != 0) { /* XXX */
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to initialize mutex");
		goto stream_new_done;
	}

	if (pthread_cond_init(&stream->buffer_full_cond, NULL)) { /* XXX */
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to initialize condition variable");
		goto stream_new_done;
	}

	LOG_INFO(LOG_TAG, "engine_stream_new: allocating input engine (0x%08x).", (int)engine->input);
	engine_error_code = engine->input->stream_create(engine, stream, media_path, input_data_callback, input_state_callback, stream);
	if (engine_error_code != ENGINE_OK) {
		goto stream_new_done;
	}

	LOG_INFO(LOG_TAG, "engine_stream_new: allocating output engine (0x%08x).", (int)engine->output);
	engine_error_code = engine->output->stream_create(engine, stream,
			engine->param_stream_type, engine->param_stream_latency,
			output_data_callback, output_state_callback, stream);

stream_new_done:
	return engine_error_code;
}

int engine_stream_delete(engine_stream_context_s * stream) {
	if (stream == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}
	LOG_INFO(LOG_TAG, "engine_stream_delete: deleting stream.");

	circular_buffer_delete(&stream->audio_buffer);
	stream->engine->input->stream_destroy(stream);
	stream->engine->output->stream_destroy(stream);

	pthread_mutex_destroy(&stream->buffer_lock);
	pthread_cond_destroy(&stream->buffer_full_cond);

	return ENGINE_OK;
}

int engine_stream_start(engine_stream_context_s * stream) {
	LOG_INFO(LOG_TAG, "engine_stream_start");

	if (stream == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	stream->decoder_is_waiting = 0;

	if (stream->engine->input->stream_start(stream) < 0) {
		LOG_ERROR(LOG_TAG, "Unable to start input engine");
		return ENGINE_GENERIC_ERROR;
	}

/*
TODO: !!!
	while (stream->audio_buffer.used * 4 < stream->audio_buffer.used * 1) {
		sleep(10);
	}
*/

	if (stream->engine->output->stream_start(stream) < 0) {
		LOG_ERROR(LOG_TAG, "Unable to start output engine");
		return ENGINE_GENERIC_ERROR;
	}

	return ENGINE_OK;
}

int engine_stream_stop(engine_stream_context_s * stream) {
	LOG_INFO(LOG_TAG, "engine_stream_stop()");

	if (stream == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	LOG_INFO(LOG_TAG, "engine_stream_stop() : stopping input stream");
	stream->engine->input->stream_stop(stream);

	LOG_INFO(LOG_TAG, "engine_stream_stop() : stopping output stream");
	stream->engine->output->stream_stop(stream);


	return ENGINE_OK;
}

int engine_stream_get_position(engine_stream_context_s * stream, int64_t * position) {
	LOG_INFO(LOG_TAG, "engine_stream_get_position()");
	if (stream == NULL || position == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	*position = (stream->last_timestamp * 1000) / stream->engine->param_sampling_rate;
	return ENGINE_OK;
}

int engine_stream_set_position(engine_stream_context_s * stream, int64_t position) {
    LOG_INFO(LOG_TAG, "engine_stream_set_position()");

	/* sync */ pthread_mutex_lock(&stream->buffer_lock);
	stream->last_timestamp = position;
	stream->engine->input->stream_set_position(stream, position);
	LOG_INFO(LOG_TAG, "engine_stream_set_position(pos=%"PRIi64", ts=%"PRIi64")", position, stream->last_timestamp);
	stream->engine->output->stream_flush(stream);
	circular_buffer_clear(&stream->audio_buffer);
	/* sync */ pthread_mutex_unlock(&stream->buffer_lock);

	/* TODO: add buffer reconstruction */

	return ENGINE_OK; /* TODO: add error checking */
}

int engine_stream_get_duration(engine_stream_context_s * stream, int64_t * duration) {
	if (stream == NULL || duration == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	if (stream->engine->input->stream_get_duration(stream, duration) < 0) {
		return ENGINE_GENERIC_ERROR;
	}

	return ENGINE_OK;
}
