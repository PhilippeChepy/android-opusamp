#include <audio_engine/processor.h>
#include <audio_engine/effects/equalizer.h>
#include <audio_engine/utils/memory.h>
#include <audio_engine/utils/log.h>

#include <stdint.h>
#include <pthread.h>
#include <math.h>


#define LOG_TAG "audio_processor:engine.c"

#define CLAMP(x, l, h) (((x) > (h)) ? (h) : (((x) < (l)) ? (l) : (x)))

enum {
    EQUALIZER_BAND_TYPE_PEAK,
    EQUALIZER_BAND_TYPE_LOW_SHELF,
    EQUALIZER_BAND_TYPE_HIGH_SHELF
};

#define LOWEST_FREQ 20.0f
#define HIGHEST_FREQ 20000.0f


typedef struct {
    float x1, x2;          /* history of input values for a filter */
    float y1, y2;          /* history of output values for a filter */
} int16_history_s;

struct equalizer_context_;

typedef struct {
    int type;

    float frequency;
    float gain;
    float width;

    float b1, b2; /* iir coefficients for outputs */
    float a0, a1, a2; /* iir coefficients for inputs */

    struct equalizer_context_ * parent;
} equalizer_band_context_s;

typedef struct equalizer_context_ {
    pthread_mutex_t band_lock;

    equalizer_band_context_s * bands;
    size_t band_count;

    void * history;
    size_t history_size;

    int needs_new_coefficients;

    processor_context_s * parent;
} equalizer_context_s;

/*
 * Computation functions
 */
int equalizer_band_new(equalizer_band_context_s * equalizer_band);
int equalizer_band_delete(equalizer_band_context_s * equalizer_band);

/* Filter taken from
 *
 * The Equivalence of Various Methods of Computing
 * Biquad Coefficients for Audio Parametric Equalizers
 *
 * by Robert Bristow-Johnson
 *
 * http://www.aes.org/e-lib/browse.cfm?elib=6326
 * http://www.musicdsp.org/files/EQ-Coefficients.pdf
 * http://www.musicdsp.org/files/Audio-EQ-Cookbook.txt
 *
 * The bandwidth method that we use here is the preferred
 * one from this article transformed from octaves to frequency
 * in Hz.
 */
static float arg_to_scale(float arg) {
    return pow(10.0f, arg / 40.0f);
}

static float calculate_omega(float frequency, float rate) {
    float omega;

    if (frequency / rate >= 0.5f) {
        omega = M_PI;
    }
    else if (frequency <= 0.0) {
        omega = 0.0f;
    }
    else {
        omega = 2.0f * M_PI * (frequency / rate);
    }

    return omega;
}

static float equalizer_band_calculate_bandwidth(equalizer_band_context_s * equalizer_band_context, int rate) {
    float bandwidth = 0.0f;

    if (equalizer_band_context->width / rate >= 0.5) {
        /* If bandwidth == 0.5 the calculation below fails as tan(G_PI/2)
         * is undefined. So set the bandwidth to a slightly smaller value.
         */
        bandwidth = M_PI - 0.00000001;
    } else if (equalizer_band_context->width <= 0.0) {
        /* If bandwidth == 0 this band won't change anything so set
         * the coefficients accordingly. The coefficient calculation
         * below would create coefficients that for some reason amplify
         * the band.
         */
        equalizer_band_context->a0 = 1.0;
        equalizer_band_context->a1 = 0.0;
        equalizer_band_context->a2 = 0.0;
        equalizer_band_context->b1 = 0.0;
        equalizer_band_context->b2 = 0.0;
    } else {
        bandwidth = 2.0 * M_PI * (equalizer_band_context->width / rate);
    }

    return bandwidth;
}


static void equalizer_band_setup_peak_filter(equalizer_band_context_s * equalizer_band_context) {
    equalizer_context_s * equalizer_context = equalizer_band_context->parent;

    int rate = equalizer_context->parent->sampling_rate;
    if (!rate) {
        return;
    }

    float gain, omega, bandwidth;
    float alpha, alpha1, alpha2, b0;

    gain = arg_to_scale(equalizer_band_context->gain);
    omega = calculate_omega(equalizer_band_context->frequency, rate);

    bandwidth = equalizer_band_calculate_bandwidth(equalizer_band_context, rate);
    if (bandwidth != 0.0f) {
        alpha = tan(bandwidth / 2.0f);

        alpha1 = alpha * gain;
        alpha2 = alpha / gain;

        b0 = (1.0f + alpha2);

        equalizer_band_context->a0 = (1.0f + alpha1) / b0;
        equalizer_band_context->a1 = (-2.0f * cos(omega)) / b0;
        equalizer_band_context->a2 = (1.0f - alpha1) / b0;
        equalizer_band_context->b1 = (2.0f * cos(omega)) / b0;
        equalizer_band_context->b2 = -(1.0f - alpha2) / b0;
    }

    LOG_INFO(LOG_TAG, "peak_filter: g=%5.1f, w=%7.2f, freq=%7.2f, a0=%7.5g, a1=%7.5g, a2=%7.5g, b1=%7.5g, b2=%7.5g",
            equalizer_band_context->gain, equalizer_band_context->width, equalizer_band_context->frequency,
            equalizer_band_context->a0, equalizer_band_context->a1, equalizer_band_context->a2,
            equalizer_band_context->b1, equalizer_band_context->b2);
}

static void equalizer_band_setup_low_shelf_filter(equalizer_band_context_s * equalizer_band_context) {
    equalizer_context_s * equalizer_context = equalizer_band_context->parent;

    int rate = equalizer_context->parent->sampling_rate;
    if (!rate) {
        return;
    }

    float gain, omega, bandwidth;
    float alpha, delta, b0;
    float egp, egm;

    gain = arg_to_scale(equalizer_band_context->gain);
    omega = calculate_omega(equalizer_band_context->frequency, rate);
    bandwidth = equalizer_band_calculate_bandwidth(equalizer_band_context, rate);
    if (bandwidth != 0.0f) {
        egm = gain - 1.0f;
        egp = gain + 1.0f;
        alpha = tan(bandwidth / 2.0f);

        delta = 2.0f * sqrt(gain) * alpha;
        b0 = egp + egm * cos(omega) + delta;

        equalizer_band_context->a0 = ((egp - egm * cos(omega) + delta) * gain) / b0;
        equalizer_band_context->a1 = ((egm - egp * cos(omega)) * 2.0f * gain) / b0;
        equalizer_band_context->a2 = ((egp - egm * cos(omega) - delta) * gain) / b0;
        equalizer_band_context->b1 = ((egm + egp * cos(omega)) * 2.0f) / b0;
        equalizer_band_context->b2 = -((egp + egm * cos(omega) - delta)) / b0;
    }

    LOG_INFO(LOG_TAG, "low_shelf_filter: g=%5.1f, w=%7.2f, freq=%7.2f, a0=%7.5g, a1=%7.5g, a2=%7.5g, b1=%7.5g, b2=%7.5g",
            equalizer_band_context->gain, equalizer_band_context->width, equalizer_band_context->frequency,
            equalizer_band_context->a0, equalizer_band_context->a1, equalizer_band_context->a2,
            equalizer_band_context->b1, equalizer_band_context->b2);
}

static void equalizer_band_setup_high_shelf_filter(equalizer_band_context_s * equalizer_band_context) {
    equalizer_context_s * equalizer_context = equalizer_band_context->parent;

    int rate = equalizer_context->parent->sampling_rate;
    if (!rate) {
        return;
    }

    float gain, omega, bandwidth;
    float alpha, delta, b0;
    float egp, egm;

    gain = arg_to_scale (equalizer_band_context->gain);
    omega = calculate_omega(equalizer_band_context->frequency, rate);
    bandwidth = equalizer_band_calculate_bandwidth(equalizer_band_context, rate);

    if (bandwidth != 0.0f) {
        egm = gain - 1.0f;
        egp = gain + 1.0f;
        alpha = tan(bandwidth / 2.0f);

        delta = 2.0f * sqrt(gain) * alpha;
        b0 = egp - egm * cos(omega) + delta;

        equalizer_band_context->a0 = ((egp + egm * cos(omega) + delta) * gain) / b0;
        equalizer_band_context->a1 = ((egm + egp * cos(omega)) * -2.0f * gain) / b0;
        equalizer_band_context->a2 = ((egp + egm * cos(omega) - delta) * gain) / b0;
        equalizer_band_context->b1 = ((egm - egp * cos(omega)) * -2.0f) / b0;
        equalizer_band_context->b2 = -((egp - egm * cos(omega) - delta)) / b0;
    }

    LOG_INFO(LOG_TAG, "g=%5.1f, w=%7.2f, freq=%7.2f, a0=%7.5g, a1=%7.5g, a2=%7.5g, b1=%7.5g, b2=%7.5g",
            equalizer_band_context->gain, equalizer_band_context->width, equalizer_band_context->frequency,
            equalizer_band_context->a0, equalizer_band_context->a1, equalizer_band_context->a2,
            equalizer_band_context->b1, equalizer_band_context->b2);
}



/* Must be called with bands_lock */
static void equalizer_set_passthrough(equalizer_context_s * equalizer_context) {
    size_t band_index = 0;
    int passthrough = 0;

    for (band_index = 0; band_index < equalizer_context->band_count; band_index++) {
        passthrough = passthrough && (equalizer_context->bands[band_index].gain == 0.0f);
    }

    equalizer_context->parent->is_pass_through = passthrough;
    LOG_DEBUG(LOG_TAG, "Passthrough mode: %i", passthrough);
}

/* Must be called with bands_lock */
static void equalizer_update_coefficients(equalizer_context_s * equalizer_context) {
    size_t band_index;

    for (band_index = 0; band_index < equalizer_context->band_count; band_index++) {
        switch (equalizer_context->bands[band_index].type) {
        case EQUALIZER_BAND_TYPE_LOW_SHELF:
            equalizer_band_setup_low_shelf_filter(&equalizer_context->bands[band_index]);
            break;
        case EQUALIZER_BAND_TYPE_HIGH_SHELF:
            equalizer_band_setup_high_shelf_filter(&equalizer_context->bands[band_index]);
            break;
        //EQUALIZER_BAND_TYPE_PEAK:
        default:
            equalizer_band_setup_peak_filter(&equalizer_context->bands[band_index]);
            break;
        }
    }

    equalizer_context->needs_new_coefficients = 0;
}

/* Must be called with transform lock! */
static void equalizer_alloc_history(equalizer_context_s * equalizer_context) {
    memory_free(equalizer_context->history);
    equalizer_context->history = memory_zero_alloc(equalizer_context->history_size * equalizer_context->parent->channel_count * equalizer_context->band_count);
}

static void equalizer_compute_frequencies(equalizer_context_s * equalizer_context, size_t band_count)
{
    size_t old_count, band_index;
    float freq0, freq1, step;

    /* sync */ pthread_mutex_lock(&equalizer_context->band_lock);

    old_count = equalizer_context->band_count;
    if (equalizer_context->band_count == band_count) {
        /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);
        return;
    }

    LOG_DEBUG(LOG_TAG, "equalizer bands (from=%u, to=%u)", old_count, band_count);
    equalizer_context->band_count = band_count;

    equalizer_band_context_s * old_bands = equalizer_context->bands;

    if (old_count < band_count) { /* case 1: adding band(s) */
        equalizer_context->bands = memory_zero_alloc(sizeof(equalizer_band_context_s) * band_count);
        memory_copy(equalizer_context->bands, old_bands, sizeof(equalizer_band_context_s) * band_count);
        memory_free(old_bands);

        for (band_index = old_count; band_index < band_count; band_index++) {
            LOG_DEBUG(LOG_TAG, "adding band %u", band_index);

            equalizer_band_new(&equalizer_context->bands[band_index]);
            equalizer_context->bands[band_index].parent = equalizer_context;
        }
    }
    else { /* case 2: removing band(s) */
        for (band_index = band_count; band_index < old_count; band_index++) {
            LOG_DEBUG(LOG_TAG, "removing band %u", band_index);

            equalizer_band_delete(&old_bands[band_index]);
            old_bands[band_index].parent = NULL;
        }

        equalizer_context->bands = memory_zero_alloc(sizeof(equalizer_band_context_s) * band_count);
        memory_copy(equalizer_context->bands, old_bands, sizeof(equalizer_band_context_s) * band_count);
        memory_free(old_bands);
    }

    equalizer_alloc_history(equalizer_context);

    step = pow(HIGHEST_FREQ / LOWEST_FREQ, 1.0 / band_count);
    freq0 = LOWEST_FREQ;

    for (band_index = 0; band_index < band_count; band_index++) {
        freq1 = freq0 * step;

        if (band_index == 0) {
            equalizer_context->bands[band_index].type = EQUALIZER_BAND_TYPE_LOW_SHELF;
        }
        else if (band_index == band_count - 1) {
            equalizer_context->bands[band_index].type = EQUALIZER_BAND_TYPE_HIGH_SHELF;
        }
        else {
            equalizer_context->bands[band_index].type = EQUALIZER_BAND_TYPE_PEAK;
        }

        equalizer_context->bands[band_index].frequency = freq0 + ((freq1 - freq0) / 2.0);
        equalizer_context->bands[band_index].width = freq1 - freq0;

        LOG_DEBUG(LOG_TAG, "band[%u] (frequency=%f)", band_index, equalizer_context->bands[band_index].frequency);

        freq0 = freq1;
    }

    equalizer_context->needs_new_coefficients = 1;
    /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);
}

static float equalizer_band_process_one_step_int16(equalizer_band_context_s * band, int16_history_s *history, float input) {                                                                       \
    /* calculate output */
    float output =
        band->a0 * input +
        band->a1 * history->x1 + band->a2 * history->x2 +
        band->b1 * history->y1 + band->b2 * history->y2;

    /* update history */
    history->y2 = history->y1;
    history->y1 = output;
    history->x2 = history->x1;
    history->x1 = input;
    return output;
}

/*
 * Interface functions
 */
int equalizer_band_new(equalizer_band_context_s * equalizer_band) {
    equalizer_band->type = EQUALIZER_BAND_TYPE_PEAK;

    equalizer_band->frequency = 0.0f;
    equalizer_band->gain = 0.0f;
    equalizer_band->width = 1.0f;

    return ENGINE_OK;
}

int equalizer_band_delete(equalizer_band_context_s * equalizer_band) {
    return ENGINE_OK;
}

/*
 * Setting eq band gain. gain in [-24.0dB, 12.0dB]
 */
int equalizer_band_set_gain(equalizer_band_context_s * equalizer_band, float gain) {
    if (gain != equalizer_band->gain) {
        equalizer_context_s * equalizer = equalizer_band->parent;

        /* sync */ pthread_mutex_lock(&equalizer->band_lock);
            equalizer->needs_new_coefficients = 1;
            equalizer_band->gain = gain;
        /* sync */ pthread_mutex_unlock(&equalizer->band_lock);
    }

    return ENGINE_OK;
}

/*
 * Setting eq band gain. gain in [0.0, 100'000.0]
 */
int equalizer_band_set_frequency(equalizer_band_context_s * equalizer_band, float frequency) {
    if (frequency != equalizer_band->frequency) {
        equalizer_context_s * equalizer = equalizer_band->parent;

        /* sync */ pthread_mutex_lock(&equalizer->band_lock);
            equalizer->needs_new_coefficients = 1;
            equalizer_band->frequency = frequency;
        /* sync */ pthread_mutex_unlock(&equalizer->band_lock);
    }

    return ENGINE_OK;
}

/*
 * Setting distance between two band edges. in [0.0, 100'000.0]
 */
int equalizer_band_set_width(equalizer_band_context_s * equalizer_band_context, float width) {
    if (width != equalizer_band_context->width) {
        equalizer_context_s * equalizer_context = equalizer_band_context->parent;

        /* sync */ pthread_mutex_lock(&equalizer_context->band_lock);
            equalizer_context->needs_new_coefficients = 1;
            equalizer_band_context->width = width;
        /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);
    }

    return ENGINE_OK;
}

int equalizer_new(processor_context_s * processor_context, int sample_rate, int channel_count) {
    equalizer_context_s * equalizer_context = memory_zero_alloc(sizeof(equalizer_context_s));
    if (!equalizer_context) {
        return ENGINE_ALLOC_ERROR;
    }

    processor_context->processor_specific = equalizer_context;
    equalizer_context->parent = processor_context;

    pthread_mutex_init(&(equalizer_context->band_lock), NULL);

    equalizer_context->bands = NULL;
    equalizer_context->band_count = 0;

    equalizer_context->history = NULL;
    equalizer_context->history_size = sizeof(int16_history_s);  /* int16 sample format */

    equalizer_context->needs_new_coefficients = 1;

    processor_context->sampling_rate = sample_rate;
    processor_context->channel_count = channel_count;

    /* equalizer_alloc_history(equalizer_context); */
    equalizer_compute_frequencies(equalizer_context, 10);
    return ENGINE_OK;
}

int equalizer_delete(processor_context_s * processor_context) {
	if (processor_context == NULL || processor_context->processor_specific == NULL) {
        return ENGINE_INVALID_PARAMETER_ERROR;
	}

	equalizer_context_s * equalizer_context = processor_context->processor_specific;
    /*
	size_t band_index = 0;

    for ( ; band_index < equalizer_context->band_count ; band_index++) {
        equalizer_band_delete();
    }
    */
	equalizer_context->band_count = 0;

    memory_free(equalizer_context->bands);
    equalizer_context->bands = NULL;

    memory_free(equalizer_context->history);
    equalizer_context->history = NULL;

    pthread_mutex_destroy(&(equalizer_context->band_lock));
    return ENGINE_OK;
}

int equalizer_get_name(processor_context_s * processor_context, char ** output_name) {
	*output_name = "Direct Form 10-band IIR Equalizer";
	return ENGINE_OK;
}

equalizer_band_context_s * equalizer_get_band(equalizer_context_s * equalizer_context, size_t band_index) {
    /* sync */ pthread_mutex_lock(&equalizer_context->band_lock);
        if (band_index >= equalizer_context->band_count) {
                /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);
            return NULL;
        }

        equalizer_band_context_s * band = &(equalizer_context->bands[band_index]);
    /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);
    return band;
}

#define equalizer_get_band_count(m_equalizer) ((m_equalizer)->band_count)


static int  equalizer_process(processor_context_s * processor_context, uint8_t *data, size_t size) {
    equalizer_context_s *equalizer_context = processor_context->processor_specific;
    uint32_t channel_count = processor_context->channel_count;
    uint32_t band_count = equalizer_context->band_count;

    uint32_t frame_count = size / channel_count / sizeof (uint16_t);

    size_t frame_index, channel_index, band_index;
    float cur;

    /* sync */ pthread_mutex_lock(&equalizer_context->band_lock);
        int need_new_coefficients = equalizer_context->needs_new_coefficients;
    /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);

    if (!need_new_coefficients && processor_context->is_pass_through) {
        return ENGINE_OK;
    }

    /* sync */ pthread_mutex_lock(&equalizer_context->band_lock);
    if (need_new_coefficients) {
        equalizer_update_coefficients(equalizer_context);
        equalizer_set_passthrough(equalizer_context);
    }
    /* sync */ pthread_mutex_unlock(&equalizer_context->band_lock);

    for (frame_index = 0; frame_index < frame_count; frame_index++) {
        int16_history_s * history = equalizer_context->history;

        for (channel_index = 0; channel_index < channel_count; channel_index++) {
            cur = *((uint16_t *) data);
            cur = cur - 32768.0f; /* convertion from unsigned to signed PCM */

            for (band_index = 0; band_index < band_count; band_index++) {
                cur = equalizer_band_process_one_step_int16(&equalizer_context->bands[band_index], history, cur);
                history++;
            }

            cur = CLAMP (cur, -32768.0, 32767.0);
            *((uint16_t *) data) = (uint16_t) floor (cur + 32767.0f); /* convertion from signed to unsigned sample PCM */
            data += sizeof (uint16_t);
        }
    }

    return ENGINE_OK;
}


int equalizer_set_property(processor_context_s * processor_context, int property_key, void * property_data) {
    switch (property_key) {
    case EQUALIZER_BAND1_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 0), *((float *)property_data));
        break;
    case EQUALIZER_BAND2_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 1), *((float *)property_data));
        break;
    case EQUALIZER_BAND3_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 2), *((float *)property_data));
        break;
    case EQUALIZER_BAND4_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 3), *((float *)property_data));
        break;
    case EQUALIZER_BAND5_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 4), *((float *)property_data));
        break;
    case EQUALIZER_BAND6_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 5), *((float *)property_data));
        break;
    case EQUALIZER_BAND7_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 6), *((float *)property_data));
        break;
    case EQUALIZER_BAND8_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 7), *((float *)property_data));
        break;
    case EQUALIZER_BAND9_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 8), *((float *)property_data));
        break;
    case EQUALIZER_BAND10_GAIN:
        equalizer_band_set_gain(equalizer_get_band(processor_context->processor_specific, 9), *((float *)property_data));
        break;
    default:
        return ENGINE_GENERIC_ERROR;
    }

    return ENGINE_OK;
}

static processor_s const equalizer_processor = {
	.processor_new = equalizer_new,
	.processor_delete = equalizer_delete,
	.processor_get_name = equalizer_get_name,

    .processor_set_property = equalizer_set_property,
    .processor_process = equalizer_process
};

processor_s const * get_equalizer_processor() {
    return &equalizer_processor;
}
