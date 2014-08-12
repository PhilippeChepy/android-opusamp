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
#include <audio_engine/outputs/audiotrack.h>
#include <audio_engine/outputs/transcoder.h>
#include <audio_engine/inputs/ffinput.h>
#include <audio_engine/utils/log.h>

#include <fcntl.h>
#include <unistd.h>
#include <inttypes.h>

#define LOG_TAG "(jni).engine"

long input_data_callback(engine_stream_context_s * stream_context, void * user_context, void * data_buffer, size_t data_length) {
	size_t total_write_length = 0;

	do {
		if (stream_context->decoder_is_stopping) {
			break;
		}

		/* sync */ pthread_mutex_lock(&stream_context->buffer_lock);
		if (stream_context->audio_buffer.used >= stream_context->engine->param_sleep_decoder_buffer_threshold) {
			/* sync */ stream_context->decoder_is_waiting = 1;
			if (!stream_context->decoder_is_stopping) {
				/* sync */ pthread_cond_wait(&stream_context->buffer_full_cond, &stream_context->buffer_lock);
			}
		}
		size_t write_length = data_length - total_write_length;
		circular_buffer_write(&stream_context->audio_buffer, &((char *)data_buffer)[total_write_length], &write_length);
		total_write_length += write_length;
		/* sync */ pthread_mutex_unlock(&stream_context->buffer_lock);
	} while (total_write_length < data_length);

	return (long)total_write_length;
}

void input_state_callback(engine_stream_context_s * stream_context, void * user_context, int stream_state) {
	LOG_INFO(LOG_TAG, "input_state_callback: %i", stream_state);

	pthread_mutex_lock(&stream_context->state_lock);
	stream_context->input_stream_state = stream_state;

	if ((stream_context->input_stream_state == STREAM_STATE_TERMINATED) && (stream_context->output_stream_state != STREAM_STATE_STARTED)) {
		stream_context->input_stream_state = STREAM_STATE_STOPPED;

		LOG_INFO(LOG_TAG, "input_state_callback: playback terminated");
		stream_context->engine->completion_callback(stream_context);
	}
	pthread_mutex_unlock(&stream_context->state_lock);
}

void output_state_callback(engine_stream_context_s * stream_context, void * user_context, int stream_state) {
	LOG_INFO(LOG_TAG, "output_state_callback: %i", stream_state);

	pthread_mutex_lock(&stream_context->state_lock);
	stream_context->output_stream_state = stream_state;

	if ((stream_context->input_stream_state == STREAM_STATE_TERMINATED) && (stream_context->output_stream_state != STREAM_STATE_STARTED)) {
		stream_context->input_stream_state = STREAM_STATE_STOPPED;

		LOG_INFO(LOG_TAG, "output_state_callback: playback terminated");
		stream_context->engine->completion_callback(stream_context);
	}
	pthread_mutex_unlock(&stream_context->state_lock);
}

long output_data_callback(engine_stream_context_s * stream_context, void * user_context, void * data_buffer, size_t nb_samples) {
	size_t total_read_length = 0;
	size_t data_length = nb_samples * stream_context->engine->param_channel_count;

	do {
		/* sync */ pthread_mutex_lock(&stream_context->buffer_lock);

		/* samples are s16 integers */
		size_t read_length = data_length * sizeof(int16_t) - total_read_length;

		circular_buffer_read(&stream_context->audio_buffer, &((int16_t *)data_buffer)[total_read_length], &read_length);
		total_read_length += read_length;

		/* waking up decoder thread if buffer is at  "param_wake_decoder_buffer_threshold" capacity */
		if (stream_context->audio_buffer.used < stream_context->engine->param_wake_decoder_buffer_threshold &&
				stream_context->decoder_is_waiting) {
			/* sync */ stream_context->decoder_is_waiting = 0;
			/* sync */ pthread_cond_broadcast(&stream_context->buffer_full_cond);
		}
		/* sync */ pthread_mutex_unlock(&stream_context->buffer_lock);

		if (stream_context->input_stream_state != ENGINE_OK) {
			break;
		}
	} while (total_read_length < data_length);

	return (total_read_length / sizeof(int16_t)) / stream_context->engine->param_channel_count;
}

int engine_new(engine_context_s * engine_context, int is_transcoder) {
	engine_output_s const * output;
	engine_input_s const * input;
	int engine_error_code = ENGINE_GENERIC_ERROR;
	char * engine_name;

	input = ffinput_get_input();
	engine_error_code = input->engine_new(engine_context);
	if (engine_error_code != ENGINE_OK) {
		LOG_INFO(LOG_TAG, "engine_new: input ffinput is returning an error.");
		goto engine_new_done;
	}
	else {
		LOG_INFO(LOG_TAG, "engine_new: using ffinput input.");
	}

/*	output = opensl_get_output(); / * >= API9 (gingerbread) + */
/*	output = audiotrack_get_output(); / * <= API9 (gingerbread) */
    if (is_transcoder) {
	    output = transcoder_get_output();
	}
	else {
	    output = safetrack_get_output(); /* otherwise (low performences */
	}
	output->engine_get_name(engine_context, &engine_name);
	engine_error_code = output->engine_new(engine_context);

	if (engine_error_code == ENGINE_OK) {
	    LOG_INFO(LOG_TAG, "engine_new: using '%s' output.", engine_name);
		engine_context->input = input;
		engine_context->output = output;
		engine_context->is_transcoder = is_transcoder;

		engine_context->completion_callback = NULL;
		engine_context->param_buffer_size = 100 * 1024;
		engine_context->param_sleep_decoder_buffer_threshold = 100 * 1024;
		engine_context->param_wake_decoder_buffer_threshold = 40  * 1024;
	}
    else {
        LOG_INFO(LOG_TAG, "engine_new: output '%s' is not available.", engine_name);
    }

engine_new_done:
	return engine_error_code;
}

int engine_delete(engine_context_s * engine_context) {
	LOG_INFO(LOG_TAG, "engine_delete: deleting engine.");
	if (engine_context == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	return engine_context->output->engine_delete(engine_context);
}

int engine_set_params(engine_context_s * engine_context, int sample_format, int sampling_rate, int channel_count, int stream_type, int stream_latency) {
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

	engine_context->param_sample_format = sample_format;
	engine_context->param_sampling_rate = sampling_rate;
	engine_context->param_channel_count = channel_count;
	engine_context->param_stream_type = stream_type;
	engine_context->param_stream_latency = stream_latency;

	engine_error_code = ENGINE_OK;

engine_set_params_done:
	return engine_error_code;
}

int engine_set_completion_callback(engine_context_s * engine_context, engine_completion_callback callback) {
	engine_context->completion_callback = callback;
	return ENGINE_OK;
}

int engine_set_timestamp_callback(engine_context_s * engine_context, engine_timestamp_callback callback) {
	engine_context->timestamp_callback = callback;
	return ENGINE_OK;
}

int engine_get_output_name(engine_context_s * engine_context, char ** output_name) {
	if (engine_context == NULL || output_name == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	return engine_context->output->engine_get_name(engine_context, output_name);
}

int engine_get_input_name(engine_context_s * engine_context, char ** input_name) {
	if (engine_context == NULL || input_name == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	return engine_context->input->engine_get_name(engine_context, input_name);
}

int engine_get_max_channel_count(engine_context_s * engine_context, uint32_t * max_channels) {
	uint32_t output_max;
	uint32_t input_max;
	int error_code;

	if (engine_context == NULL || max_channels == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	error_code = engine_context->output->engine_get_max_channel_count(engine_context, &output_max);
	if (error_code != ENGINE_OK) {
		return error_code;
	}
	error_code = engine_context->input->engine_get_max_channel_count(engine_context, &input_max);
	if (error_code != ENGINE_OK) {
		return error_code;
	}

	*max_channels = input_max < output_max ? input_max : output_max;

	return ENGINE_OK;
}

int engine_stream_new(engine_context_s * engine_context, engine_stream_context_s * stream_context, const char * media_path) {
	int engine_error_code = ENGINE_GENERIC_ERROR;

	LOG_INFO(LOG_TAG, "engine_stream_new: creating a new stream.");

	if (engine_context == NULL || stream_context == NULL) {
		engine_error_code = ENGINE_INVALID_PARAMETER_ERROR;
		LOG_ERROR(LOG_TAG, "engine_stream_new: ENGINE_INVALID_PARAMETER_ERROR");
		goto stream_new_done;
	}

	if (circular_buffer_new(&stream_context->audio_buffer, engine_context->param_buffer_size) != CIRCULAR_BUFFER_OK) {
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to allocate circular buffer");
		goto stream_new_done;
	}
	stream_context->engine = engine_context;

	if (pthread_mutex_init(&stream_context->buffer_lock, NULL) != 0) { /* XXX */
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to initialize mutex");
		goto stream_new_done;
	}

	if (pthread_mutex_init(&stream_context->state_lock, NULL) != 0) { /* XXX */
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to initialize mutex");
		goto stream_new_done;
	}

	if (pthread_cond_init(&stream_context->buffer_full_cond, NULL)) { /* XXX */
		LOG_ERROR(LOG_TAG, "engine_stream_new: unable to initialize condition variable");
		goto stream_new_done;
	}

	LOG_INFO(LOG_TAG, "engine_stream_new: allocating input engine (0x%08x).", (int)engine_context->input);
	engine_error_code = engine_context->input->engine_stream_new(engine_context, stream_context, media_path,
			input_data_callback, input_state_callback, stream_context);
	if (engine_error_code != ENGINE_OK) {
		goto stream_new_done;
	}

	LOG_INFO(LOG_TAG, "engine_stream_new: allocating output engine (0x%08x).", (int)engine_context->output);
	engine_error_code = engine_context->output->engine_stream_new(engine_context, stream_context,
			engine_context->param_stream_type, engine_context->param_stream_latency,
			output_data_callback, output_state_callback, stream_context);

stream_new_done:
	return engine_error_code;
}

int engine_stream_delete(engine_stream_context_s * stream_context) {
	if (stream_context == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}
	LOG_INFO(LOG_TAG, "engine_stream_delete: deleting stream.");

	circular_buffer_delete(&stream_context->audio_buffer);
	stream_context->engine->input->engine_stream_delete(stream_context);
	stream_context->engine->output->engine_stream_delete(stream_context);

	pthread_mutex_destroy(&stream_context->buffer_lock);
	pthread_cond_destroy(&stream_context->buffer_full_cond);

	return ENGINE_OK;
}

int engine_stream_start(engine_stream_context_s * stream_context) {
	LOG_INFO(LOG_TAG, "engine_stream_start");

	if (stream_context == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	stream_context->decoder_is_waiting = 0;

	if (stream_context->engine->input->engine_stream_start(stream_context) < 0) {
		LOG_ERROR(LOG_TAG, "Unable to start input engine");
		return ENGINE_GENERIC_ERROR;
	}


	while (stream_context->audio_buffer.used * 4 < stream_context->audio_buffer.used * 1) {
		sleep(10);
	}


	if (stream_context->engine->output->engine_stream_start(stream_context) < 0) {
		LOG_ERROR(LOG_TAG, "Unable to start output engine");
		return ENGINE_GENERIC_ERROR;
	}

	return ENGINE_OK;
}

int engine_stream_stop(engine_stream_context_s * stream_context) {
	LOG_INFO(LOG_TAG, "engine_stream_stop()");

	if (stream_context == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	LOG_INFO(LOG_TAG, "engine_stream_stop() : stopping input stream");
	stream_context->engine->input->engine_stream_stop(stream_context);

	LOG_INFO(LOG_TAG, "engine_stream_stop() : stopping output stream");
	stream_context->engine->output->engine_stream_stop(stream_context);


	return ENGINE_OK;
}

int engine_stream_get_position(engine_stream_context_s * stream_context, int64_t * position) {
	LOG_INFO(LOG_TAG, "engine_stream_get_position()");
	if (stream_context == NULL || position == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	*position = (stream_context->last_timestamp * 1000) / stream_context->engine->param_sampling_rate;
	return ENGINE_OK;
}

int engine_stream_set_position(engine_stream_context_s * stream_context, int64_t position) {
    LOG_INFO(LOG_TAG, "engine_stream_set_position()");

	/* sync */ pthread_mutex_lock(&stream_context->buffer_lock);
	stream_context->last_timestamp = position;
	stream_context->engine->input->engine_stream_set_position(stream_context, position);
	LOG_INFO(LOG_TAG, "engine_stream_set_position(pos=%"PRIi64", ts=%"PRIi64")", position, stream_context->last_timestamp);
	stream_context->engine->output->engine_stream_flush(stream_context);
	circular_buffer_clear(&stream_context->audio_buffer);
	/* sync */ pthread_mutex_unlock(&stream_context->buffer_lock);

	/* TODO: add buffer reconstruction */

	return ENGINE_OK; /* TODO: add error checking */
}

int engine_stream_get_duration(engine_stream_context_s * stream_context, int64_t * duration) {
	if (stream_context == NULL || duration == NULL) {
		return ENGINE_INVALID_PARAMETER_ERROR;
	}

	if (stream_context->engine->input->engine_stream_get_duration(stream_context, duration) < 0) {
		return ENGINE_GENERIC_ERROR;
	}

	return ENGINE_OK;
}
