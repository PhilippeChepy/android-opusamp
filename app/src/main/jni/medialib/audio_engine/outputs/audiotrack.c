/*
 * audiotrack.c
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

#include <dlfcn.h>
#include <assert.h>

#include <audio_engine/outputs/audiotrack.h>
#include <audio_engine/utils/memory.h>
#include <audio_engine/utils/log.h>

#define LOG_TAG "(jni).outputs.audiotrack"

#define AUDIOTRACK_INSTANCE_SIZE 256

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

typedef struct {
	void* (*ctor)(void* instance, int, unsigned int, int, int, int, unsigned int, void (*)(int, void*, void*), void*, int, int);
	void* (*ctor_legacy)(void* instance, int, unsigned int, int, int, int, unsigned int, void (*)(int, void*, void*), void*, int);
	void* (*dtor)(void* instance);
	uint32_t (*latency)(void* instance);
	uint32_t (*init_check)(void* instance);
	uint32_t (*get_min_frame_count)(int* frame_count, int stream_type, uint32_t rate);
	void (*start)(void* instance);
	void (*pause)(void* instance);
	void (*flush)(void * instance);
	uint32_t (*get_position)(void* instance, uint32_t* position);
	int (*get_output_frame_count)(int* frame_count, int stream);
	int (*get_output_latency)(uint32_t* frame_count, int stream);
	int (*get_output_samplingrate)(int* frame_count, int stream);
	uint32_t (*set_marker_position)(void* instance, unsigned int);

	int is_gingerbread;
} audiotrack_object_s;

/*
 * https://android.googlesource.com/platform/frameworks/base/+/android-2.2.3_r2.1/include/media/AudioTrack.h
 */
typedef struct {
	uint32_t    flags;
	int         channelCount;
	int         format;
	size_t      frameCount;
	size_t      size;

	union {
		void*       raw;
		short*      i16;
		int8_t*     i8;
	};
} audiotrack_callback_buffer_s;

enum audiotrack_event_type {
	EVENT_MORE_DATA = 0,
	EVENT_UNDERRUN = 1,
	EVENT_LOOP_END = 2,
	EVENT_MARKER = 3,
	EVENT_NEW_POS = 4,
	EVENT_BUFFER_END = 5
};

typedef struct  {
	void * library_handle;
	audiotrack_object_s audiotrack_object;
} audiotrack_output_context_s;

typedef struct {
	int stream_type;
	engine_data_callback data_callback;
	engine_state_callback state_callback;
	void * audiotrack_instance;
	void * user_context;
	size_t written;
	int draining;
} audiotrack_stream_context_s;

/* Prototype is used by feeding callback */
int audiotrack_stream_stop(engine_stream_context_s * stream_context);
int audiotrack_stream_flush(engine_stream_context_s * stream_context);
int audiotrack_stream_get_position(engine_stream_context_s * stream_context, int64_t * position);

static int audiotrack_version_is_froyo(audiotrack_output_context_s * context)
{
  return context->audiotrack_object.ctor_legacy != NULL;
}

static int audiotrack_version_is_gingerbread(audiotrack_output_context_s * context)
{
  return context->audiotrack_object.is_gingerbread;
}

static void audiotrack_data_callback(int event, void * user_context, void * info)
{
	engine_stream_context_s * stream_context = user_context;
	audiotrack_output_context_s * audiotrack_output = stream_context->engine->engine_output_specific;
	audiotrack_stream_context_s * audiotrack_stream = stream_context->stream_output_specific;
	audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;

	switch (event) {
	/*
	 * audiotrack needs more data.
	 */

	case EVENT_MORE_DATA: {
		long got = 0;
		audiotrack_callback_buffer_s * buffer = (audiotrack_callback_buffer_s *) info;

		if (audiotrack_stream->draining == 0) {
			got = audiotrack_stream->data_callback(stream_context, audiotrack_stream->user_context, buffer->raw, buffer->frameCount);
			audiotrack_stream->written += got;

			if (got != (long)buffer->frameCount) {
				audiotrack_stream->draining = 1;
				audiotrack->set_marker_position(audiotrack_stream->audiotrack_instance, audiotrack_stream->written);
			}

			stream_context->last_timestamp = stream_context->last_timestamp + got;
			stream_context->engine->timestamp_callback(stream_context, stream_context->last_timestamp);
		}
		break;
	}
	case EVENT_MARKER: {
		LOG_WARNING(LOG_TAG, "audiotrack_data_callback: EVENT_MARKER");
		if (audiotrack_stream->draining == 1) {
			audiotrack_stream_flush(stream_context);
			audiotrack_stream_stop(stream_context);
			audiotrack_stream->draining = 0;
		}
		LOG_WARNING(LOG_TAG, "audiotrack_data_callback: EVENT_MARKER : done");
		break;
	}
	/*
	 * Unhandled events.
	 */
	case EVENT_UNDERRUN:
		LOG_WARNING(LOG_TAG, "audiotrack_data_callback: unhandled event: EVENT_UNDERRUN");
		break;
	case EVENT_LOOP_END:
		LOG_WARNING(LOG_TAG, "audiotrack_data_callback: unhandled event: EVENT_LOOP_END");
		break;
	case EVENT_NEW_POS:
		LOG_WARNING(LOG_TAG, "audiotrack_data_callback: unhandled event: EVENT_NEW_POS");
		break;
	case EVENT_BUFFER_END:
		LOG_WARNING(LOG_TAG, "audiotrack_data_callback: unhandled event: EVENT_BUFFER_END");
		break;
	}
}

static int audiotrack_get_min_frame_count(engine_context_s * engine_context,
		int format, int sampling_rate, int channel_count, int stream_type, int * min_frame_count)
{
	int error_code = ENGINE_GENERIC_ERROR;
	uint32_t status_code;
	audiotrack_output_context_s * audiotrack_output = engine_context->engine_output_specific;
	audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;

	if (audiotrack_version_is_froyo(audiotrack_output)) {
		int output_sample_rate = 0;
		int output_frame_count = 0;
		uint32_t output_latency = 0;
		int output_minimum_buffer_count = 0;

		status_code = audiotrack->get_output_frame_count(&output_frame_count, stream_type);
		if (status_code != 0) {
			LOG_WARNING(LOG_TAG, "error getting output frame count");
			goto audiotrack_get_min_frame_count_done;
		}
		status_code = audiotrack->get_output_latency(&output_latency, stream_type);
		if (status_code != 0) {
			LOG_WARNING(LOG_TAG, "error getting output latency");
			goto audiotrack_get_min_frame_count_done;
		}
		status_code = audiotrack->get_output_samplingrate(&output_sample_rate, stream_type);
		if (status_code != 0) {
			LOG_WARNING(LOG_TAG, "error getting output sampling rate");
			goto audiotrack_get_min_frame_count_done;
		}

		/*
		 * https://android.googlesource.com/platform/frameworks/base/+/android-2.2.3_r2.1/media/libmedia/AudioTrack.cpp
		 */
		output_minimum_buffer_count = output_latency / ((1000 * output_frame_count) / output_sample_rate);
		output_minimum_buffer_count = output_minimum_buffer_count < 2 ? output_minimum_buffer_count : 2;
		*min_frame_count = (output_frame_count * sampling_rate * output_minimum_buffer_count) / output_sample_rate;
	}
	else {
		status_code = audiotrack->get_min_frame_count(min_frame_count, stream_type, sampling_rate);
		if (status_code != 0) {
			LOG_WARNING(LOG_TAG, "error getting minimum frame count");
			goto audiotrack_get_min_frame_count_done;
		}
	}

	error_code = ENGINE_OK;

audiotrack_get_min_frame_count_done:
	return error_code;
}

int audiotrack_new(engine_context_s * engine_context) {
	audiotrack_output_context_s * audiotrack_output = NULL;
	audiotrack_object_s * audiotrack = NULL;
	int error_code = ENGINE_GENERIC_ERROR;

	/*
	 * Allocating specific structure for this engine.
	 */
	audiotrack_output = memory_zero_alloc(sizeof *audiotrack_output);
	if (audiotrack_output == NULL) {
		LOG_INFO(LOG_TAG, "audiotrack_new: memory allocation failure");
		goto audiotrack_new_done;
	}

	/*
	 * Getting symbols from libmedia
	 */
	audiotrack_output->library_handle = dlopen("libmedia.so", RTLD_NOW);
	if (audiotrack_output->library_handle == NULL) {
		LOG_INFO(LOG_TAG, "audiotrack_new: unable to open libmedia.so : dlopen = %s", dlerror());
		goto audiotrack_new_done;
	}

#define GET_SYMBOL(ptr, symbol) ptr = dlsym(audiotrack_output->library_handle, symbol);

	audiotrack = &audiotrack_output->audiotrack_object;
	GET_SYMBOL(audiotrack->ctor, "_ZN7android10AudioTrackC1EijiiijPFviPvS1_ES1_ii");

	if (audiotrack->ctor == NULL) { /* android froyo */
		GET_SYMBOL(audiotrack->ctor_legacy, "_ZN7android10AudioTrackC1EijiiijPFviPvS1_ES1_i");
		if (audiotrack->ctor_legacy == NULL) {
			LOG_WARNING(LOG_TAG, "audiotrack_new: no suitable constructor found for audiotrack_object");
			goto audiotrack_new_done;
		}
	}

	GET_SYMBOL(audiotrack->dtor, "_ZN7android10AudioTrackD1Ev");
	GET_SYMBOL(audiotrack->latency, "_ZNK7android10AudioTrack7latencyEv");
	GET_SYMBOL(audiotrack->init_check, "_ZNK7android10AudioTrack9initCheckEv");

	/*
	 * Needed for computation of minimum frame count.
	 */
	/* <= froyo */
	GET_SYMBOL(audiotrack->get_output_frame_count, "_ZN7android11AudioSystem19getOutputFrameCountEPii");
	GET_SYMBOL(audiotrack->get_output_latency, "_ZN7android11AudioSystem16getOutputLatencyEPji");

	if (audiotrack->get_output_latency == NULL) {
		GET_SYMBOL(audiotrack->get_output_latency, "_ZN7android11AudioSystem16getOutputLatencyEPj19audio_stream_type_t");
	}

	GET_SYMBOL(audiotrack->get_output_samplingrate, "_ZN7android11AudioSystem21getOutputSamplingRateEPii");

	/* >= gingerbread */
	GET_SYMBOL(audiotrack->get_min_frame_count, "_ZN7android10AudioTrack16getMinFrameCountEPiij");
	if (audiotrack->get_min_frame_count == NULL) {
		audiotrack->is_gingerbread = 0;
		GET_SYMBOL(audiotrack->get_min_frame_count, "_ZN7android10AudioTrack16getMinFrameCountEPi19audio_stream_type_tj");
	}
	else {
		audiotrack->is_gingerbread = 1;
	}

	GET_SYMBOL(audiotrack->start, "_ZN7android10AudioTrack5startEv");
	GET_SYMBOL(audiotrack->pause, "_ZN7android10AudioTrack5pauseEv");
	GET_SYMBOL(audiotrack->flush, "_ZN7android10AudioTrack5flushEv");
	GET_SYMBOL(audiotrack->get_position, "_ZN7android10AudioTrack11getPositionEPj");
	GET_SYMBOL(audiotrack->set_marker_position, "_ZN7android10AudioTrack17setMarkerPositionEj");

	/*
	 * Checking minimum symbol requirements.
	 */
	if(!((audiotrack->ctor || audiotrack->ctor_legacy) &&
			audiotrack->dtor && audiotrack->latency && audiotrack->init_check &&
			/* at least one way to get the minimum frame count to request. */
			((audiotrack->get_output_frame_count && audiotrack->get_output_latency && audiotrack->get_output_samplingrate) ||
					audiotrack->get_min_frame_count) &&
			/* */
			audiotrack->start && audiotrack->pause && audiotrack->flush && audiotrack->get_position && audiotrack->set_marker_position)) {

		LOG_WARNING(LOG_TAG, "audiotrack_new: missing symbols in audiotrack_object");
		goto audiotrack_new_done;
	}

	engine_context->engine_output_specific = audiotrack_output;
	error_code = ENGINE_OK;

audiotrack_new_done:
	if (error_code != ENGINE_OK) {
		if (audiotrack_output != NULL) {
			memory_free(audiotrack_output);
		}
	}

	return error_code;
}

int audiotrack_delete(engine_context_s * engine_context) {
	audiotrack_output_context_s * audiotrack_output = engine_context->engine_output_specific;

	if (audiotrack_output != NULL) {
		if (audiotrack_output->library_handle != NULL) {
			dlclose(audiotrack_output->library_handle);
		}

		/*
		 * Freeing specific structure
		 */
		memory_free(audiotrack_output);
		engine_context->engine_output_specific = NULL;
	}

	return ENGINE_OK;
}

int audiotrack_get_name(engine_context_s * engine_context, char ** output_name) {
	*output_name = "Native AudioTrack audio output";
	return ENGINE_OK;
}

int audiotrack_get_max_channel_count(engine_context_s * engine_context, uint32_t * max_channels) {
	/*
		http://androidxref.com/4.2.2_r1/xref/frameworks/av/services/audioflinger/AudioFlinger.h#67
	*/
	*max_channels = 2;
	return ENGINE_OK;
}


int audiotrack_stream_new(engine_context_s * engine_context, engine_stream_context_s * stream_context,
	int stream_type, int stream_latency, engine_data_callback data_callback, engine_state_callback state_callback, void * user_context) {

	int channel_config = 0;
	int minimum_frame_count = 0;
	int error_code = ENGINE_GENERIC_ERROR;

	audiotrack_stream_context_s * audiotrack_stream = memory_zero_alloc(sizeof *audiotrack_stream);
	audiotrack_output_context_s * audiotrack_output = engine_context->engine_output_specific;
	audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;

	if (audiotrack_stream == NULL) {
		LOG_WARNING(LOG_TAG, "audiotrack_stream_new: Error allocating stream");
		goto audiotrack_stream_new_done;
	}

	if (engine_context->param_sample_format == SAMPLE_FORMAT_FLOAT32_LE || engine_context->param_sample_format == SAMPLE_FORMAT_FLOAT32_BE) {
		error_code = ENGINE_INVALID_FORMAT_ERROR;
		LOG_WARNING(LOG_TAG, "audiotrack_stream_new: ENGINE_INVALID_FORMAT_ERROR");
		goto audiotrack_stream_new_done;
	}

	if (audiotrack_get_min_frame_count(engine_context,
			engine_context->param_sample_format, engine_context->param_sampling_rate, engine_context->param_channel_count, stream_type, &minimum_frame_count)) {
		LOG_WARNING(LOG_TAG, "audiotrack_stream_new: audiotrack_get_min_frame_count() failed");
		goto audiotrack_stream_new_done;
	}

	audiotrack_stream->data_callback = data_callback;
	audiotrack_stream->state_callback = state_callback;
	audiotrack_stream->user_context = user_context;
	audiotrack_stream->stream_type = stream_type;
	audiotrack_stream->audiotrack_instance = memory_alloc(AUDIOTRACK_INSTANCE_SIZE);

	stream_context->last_timestamp = 0;

	if (audiotrack_version_is_froyo(audiotrack_output) || audiotrack_version_is_gingerbread(audiotrack_output)) {
		channel_config = engine_context->param_channel_count == 2 ? AUDIO_CHANNEL_OUT_STEREO_LEGACY : AUDIO_CHANNEL_OUT_MONO_LEGACY;
	}
	else {
		channel_config = engine_context->param_channel_count == 2 ? AUDIO_CHANNEL_OUT_STEREO : AUDIO_CHANNEL_OUT_MONO;
	}

	if (audiotrack->ctor_legacy != NULL) {
		audiotrack->ctor_legacy(audiotrack_stream->audiotrack_instance,
				audiotrack_stream->stream_type, engine_context->param_sampling_rate, AUDIO_FORMAT_PCM_16_BIT,
				channel_config, minimum_frame_count, 0, audiotrack_data_callback, stream_context, 0);
	}
	else {
		audiotrack->ctor(audiotrack_stream->audiotrack_instance,
						audiotrack_stream->stream_type, engine_context->param_sampling_rate, AUDIO_FORMAT_PCM_16_BIT,
						channel_config, minimum_frame_count, 0, audiotrack_data_callback, stream_context, 0, 0);
	}

	if (audiotrack->init_check(audiotrack_stream->audiotrack_instance) != 0) {
		LOG_WARNING(LOG_TAG, "audiotrack_stream_new: stream not correctly initialized");
		goto audiotrack_stream_new_done;
	}

	stream_context->stream_output_specific = audiotrack_stream;

	error_code = ENGINE_OK;

audiotrack_stream_new_done:
	if (error_code != ENGINE_OK) {
		if (audiotrack_stream != NULL) {
			memory_free(audiotrack_stream);
		}
		stream_context->stream_output_specific = NULL;
	}

	return error_code;
}

int audiotrack_stream_delete(engine_stream_context_s * stream_context) {
	audiotrack_stream_context_s * audiotrack_stream = stream_context->stream_output_specific;
	audiotrack_output_context_s * audiotrack_output = stream_context->engine->engine_output_specific;
	audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;

	if (audiotrack_stream != NULL) {
		if (audiotrack_stream->audiotrack_instance != NULL) {
			audiotrack->dtor(audiotrack_stream->audiotrack_instance);
			memory_free(audiotrack_stream->audiotrack_instance);
		}
		memory_free(audiotrack_stream);
		stream_context->stream_output_specific = NULL;
	}

	return ENGINE_OK;
}

int audiotrack_stream_start(engine_stream_context_s * stream_context) {
	audiotrack_stream_context_s * audiotrack_stream = stream_context->stream_output_specific;
	audiotrack_output_context_s * audiotrack_output = stream_context->engine->engine_output_specific;
	audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;

	audiotrack_stream->state_callback(stream_context, audiotrack_stream->user_context, STREAM_STATE_STARTED);

	audiotrack->start(audiotrack_stream->audiotrack_instance);

	return ENGINE_OK;
}

int audiotrack_stream_stop(engine_stream_context_s * stream_context) {
	audiotrack_stream_context_s * audiotrack_stream = stream_context->stream_output_specific;
	audiotrack_output_context_s * audiotrack_output = stream_context->engine->engine_output_specific;

	if (audiotrack_stream != NULL && audiotrack_output != NULL) {
		audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;

		/* XXX: remplace by stop(), @see : http://www.videolan.org/developers/vlc/modules/audio_output/audiotrack.c */
		audiotrack->pause(audiotrack_stream->audiotrack_instance);
		audiotrack_stream->state_callback(stream_context, audiotrack_stream->user_context, STREAM_STATE_STOPPED);
	}

	return ENGINE_OK;
}

int audiotrack_stream_flush(engine_stream_context_s * stream_context) {
	audiotrack_stream_context_s * audiotrack_stream = stream_context->stream_output_specific;
	audiotrack_output_context_s * audiotrack_output = stream_context->engine->engine_output_specific;

	if (audiotrack_stream != NULL && audiotrack_output != NULL) {
		audiotrack_object_s * audiotrack = &audiotrack_output->audiotrack_object;
		audiotrack->flush(audiotrack_stream->audiotrack_instance);
	}

	return ENGINE_OK;
}

static engine_output_s const audiotrack_output;

engine_output_s const * audiotrack_get_output() {
	return &audiotrack_output;
}

static engine_output_s const audiotrack_output = {
	.engine_new = audiotrack_new,
	.engine_delete = audiotrack_delete,
	.engine_get_name = audiotrack_get_name,
	.engine_get_max_channel_count = audiotrack_get_max_channel_count,
	.engine_stream_new = audiotrack_stream_new,
	.engine_stream_delete = audiotrack_stream_delete,
	.engine_stream_start = audiotrack_stream_start,
	.engine_stream_stop = audiotrack_stream_stop,
	/* TODO: .engine_stream_flush = audiotrack_stream_flush, */
};
