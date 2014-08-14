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
#include <libavformat/avio.h>
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

//	size_t buffer_size;
//	int16_t * buffer;

	void * user_context;
	int64_t written_samples;
} transcoder_stream_context_s;

int transcoder_stream_stop(engine_stream_context_s * stream_context);
int transcoder_stream_flush(engine_stream_context_s * stream_context);
int transcoder_stream_get_position(engine_stream_context_s * stream_context, int64_t * position);

static void * output_thread(void * thread_arg) {
	engine_stream_context_s * stream_context = thread_arg;

/**/
    AVFormatContext * output_format_context;
    AVStream * output_stream;
    AVOutputFormat *output_format;
    AVPacket packet;

    size_t stream_index;

    size_t output_buffer_size;
    size_t sample_size;
    uint8_t * output_buffer;
    int16_t * samples;

#define OUTPUT_FILENAME "/sdcard/test.ogg"
    output_format = av_guess_format(NULL, OUTPUT_FILENAME, NULL);
    output_format->audio_codec = CODEC_ID_VORBIS;

    output_format_context = avformat_alloc_context();
    output_format_context->oformat = output_format;

    AVCodec* vorbis_encoder = avcodec_find_encoder(output_format->audio_codec);
    snprintf(output_format_context->filename, sizeof(output_format_context->filename), "%s", OUTPUT_FILENAME);

    LOG_INFO(LOG_TAG, "encoder = (%8.8x)", (int)vorbis_encoder);

    output_stream = avformat_new_stream(output_format_context, vorbis_encoder);
    output_stream->codec->codec_id = output_format->audio_codec;
    output_stream->codec->codec_type = AVMEDIA_TYPE_AUDIO;
    output_stream->codec->sample_fmt = AV_SAMPLE_FMT_FLTP;
    output_stream->codec->bit_rate = 2 * 16 * 44100;
    output_stream->codec->sample_rate = 44100;
    output_stream->codec->channels = 2;

    if (output_format_context->oformat->flags & AVFMT_GLOBALHEADER) {
        output_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
    }

    AVDictionary *opts = NULL;
    av_dict_set(&opts, "strict", "experimental", 0);
    avcodec_open2(output_stream->codec, vorbis_encoder, &opts);
    av_dict_free(&opts);

    output_buffer_size = 32768; // 32K
    output_buffer = (uint8_t *)av_malloc(output_buffer_size);

    sample_size = 2 * output_stream->codec->frame_size * output_stream->codec->channels;
    samples = (int16_t *)av_malloc(sample_size);

    if (!(output_format->flags & AVFMT_NOFILE))
        avio_open(&output_format_context->pb, OUTPUT_FILENAME, AVIO_FLAG_WRITE);

    avformat_write_header(output_format_context, NULL);
/**/
	transcoder_stream_context_s * transcoder_stream = stream_context->stream_output_specific;

    get_env(stream_context, 1);

	for (;;) {
		int request_size = output_stream->codec->frame_size;
		int nb_samples = request_size / stream_context->engine->param_channel_count;
		int got = transcoder_stream->data_callback(stream_context, transcoder_stream->user_context, samples, nb_samples);

        // TODO: write transcoder_stream->buffer[0, got * stream_context->engine->param_channel_count]

        av_init_packet(&packet);

        packet.size = avcodec_encode_audio(output_stream->codec, output_buffer, output_buffer_size, samples);

        if (output_stream->codec->coded_frame && output_stream->codec->coded_frame->pts != (unsigned int)AV_NOPTS_VALUE) {
            packet.pts = av_rescale_q(output_stream->codec->coded_frame->pts, output_stream->codec->time_base, output_stream->time_base);
        }

        packet.flags |= AV_PKT_FLAG_KEY;
        packet.stream_index = output_stream->index;
        packet.data = output_buffer;
        av_interleaved_write_frame(output_format_context, &packet);


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

/**/
    av_write_trailer(output_format_context);

    if (output_stream) {
        avcodec_close(output_stream->codec);
        av_free(output_buffer);
        av_free(samples);
    }

    for (stream_index = 0; stream_index < output_format_context->nb_streams; stream_index++) {
        av_freep(&output_format_context->streams[stream_index]->codec);
        av_freep(&output_format_context->streams[stream_index]);
    }

    if (!(output_format->flags & AVFMT_NOFILE)) {
        avio_close(output_format_context->pb);
    }

    av_free(output_format_context);
/**/

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

