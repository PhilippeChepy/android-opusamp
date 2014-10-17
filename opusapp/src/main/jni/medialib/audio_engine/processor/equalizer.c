#include <pthread.h>
#include <math.h>

#include <audio_engine/utils/memory.h>
#include <audio_engine/utils/log.h>
#include <audio_engine/utils/real.h>

#include <audio_engine/processor/equalizer.h>
#include <audio_engine/processor/utils/fft.h>

#define LOG_TAG "(jni)audio_processor:equalizer.c"


#define PI 3.1415926535897932384626433832795

#define DITHERLEN 65536

#define M 15

static real_t fact[M+1];
static real_t aa = 96;
static real_t iza;

typedef struct equalizer_param_ {
	struct equalizer_param_ * next;
	int8_t left;
	int8_t right;
	float lower;
	float upper;
	float gain;
} equalizer_param_s;

typedef struct {
    real_t *lires, *lires1, *lires2;
    real_t *rires, *rires1, *rires2;
    real_t *irest;
    real_t *fsamples;
    real_t *ditherbuf;
    int ditherptr;
    volatile int chg_ires;
    volatile int cur_ires;
    int winlen;
    int winlenbit;
    int tabsize;
    int nbufsamples;
    int16_t *inbuf;
    real_t * outbuf;

    int dither;
    int fft_bits;
    int channel_count;

    pthread_mutex_t table_lock;
    int needs_table_update;

    equalizer_param_s * band_list;
} equalizer_state_s;

#define NBANDS 9
static real_t bands[] = {
    44.0, 88.0, 177.0, 355.0, 710.0, 1420.0, 2840.0, 5680.0, 11360.0
};

#define GAIN_HALF 14
#define GAIN_RANGE 28

float band_values[20] = {
    1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, // left
    1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, // right
};

//   0   = -14dB | 14 =   0dB | 28 = +14dB
int slider_positions[22] = {
    /* L-preamp */ 14,
    /* L-bands  */ 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
    /* R-preamp */ 14,
    /* R-bands  */ 14, 14, 14, 14, 14, 14, 14, 14, 14, 14,
};

#define RINT(x) ((x) >= 0 ? ((int)((x) + 0.5)) : ((int)((x) - 0.5)))


#define equalizer_param_print(__band) LOG_INFO(LOG_TAG, "[%eHz - %eHz] %edB %c%c", (__band)->lower, (__band)->upper, (__band)->gain, (__band)->left ? 'L' : '-', (__band)->right ? 'R' : '-')

void equalizer_param_create(equalizer_param_s * param, float lower, float upper, float gain) {
	param->left = 1;
	param->right = 1;
	param->lower = lower;
	param->upper = upper;
	param->gain = gain;
	//equalizer_param_print(param);
}

void equalizer_param_destroy(equalizer_param_s * param) {
    memory_free(param);
}

void equalizer_param_link(equalizer_param_s * p1, equalizer_param_s * p2) {
    p2->next = p1->next;
    p1->next = p2;
}

/*
    Utils
 */
static real_t alpha(real_t a) {
    if (a <= 21) {
        return 0;
    }

    if (a <= 50) {
        return 0.5842*pow(a-21,0.4)+0.07886*(a-21);
    }

    return 0.1102*(a-8.7);
}

static real_t izero(real_t x) {
    real_t ret = 1;
    int m;

    for(m = 1 ; m <= M ; m++) {
        real_t t = pow(x / 2, m) / fact[m];
        ret += t * t;
    }

    return ret;
}

static real_t win(real_t n,int N) {
    return izero(alpha(aa) * sqrt(1 - 4 * n * n / ((N - 1) * (N - 1)))) / iza;
}

static real_t sinc(real_t x) {
    return x == 0 ? 1 : sin(x) / x;
}

static real_t hn_lpf(int n,real_t f,real_t fs) {
    real_t t = 1 / fs;
    real_t omega = 2 * PI * f;
    return 2*f*t*sinc(n * omega * t);
}

static real_t hn_imp(int n) {
    return n == 0 ? 1.0 : 0.0;
}

static real_t hn(int n, equalizer_param_s * band_list, real_t fs) {
    equalizer_param_s *e;
    real_t ret,lhn;

    lhn = hn_lpf(n, band_list->upper, fs);
    ret = band_list->gain * lhn;

    for(e = band_list->next ; e->next != NULL && e->upper < fs / 2; e = e->next) {
        real_t lhn2 = hn_lpf(n,e->upper,fs);
        ret += e->gain*(lhn2-lhn);
        lhn = lhn2;
    }

    ret += e->gain*(hn_imp(n)-lhn);

    return ret;
}



/*
    Private interface
 */
void equalizer_impl_init(equalizer_state_s * equalizer_state, int wb, int channel_count) {
    size_t fact_index, fact_pow_index;

    if (equalizer_state->lires1 != NULL) {
        memory_free(equalizer_state->lires1);
    }

    if (equalizer_state->lires2 != NULL) {
        memory_free(equalizer_state->lires2);
    }

    if (equalizer_state->irest != NULL) {
        memory_free(equalizer_state->irest);
    }

    if (equalizer_state->fsamples != NULL) {
        memory_free(equalizer_state->fsamples);
    }

    if (equalizer_state->inbuf != NULL) {
        memory_free(equalizer_state->inbuf);
    }

    if (equalizer_state->outbuf != NULL) {
        memory_free(equalizer_state->outbuf);
    }

    if (equalizer_state->ditherbuf != NULL) {
        memory_free(equalizer_state->ditherbuf);
    }

    memory_zero(equalizer_state, sizeof(*equalizer_state));

    if (pthread_mutex_init(&equalizer_state->table_lock, NULL) != 0) { /* XXX */
        // TODO: error checking..
    }

    equalizer_state->needs_table_update = 0;

    equalizer_state->channel_count = channel_count;
    equalizer_state->winlen = (1 << (wb-1))-1;
    equalizer_state->winlenbit = wb;
    equalizer_state->tabsize  = 1 << wb;

    equalizer_state->lires1 = (real_t *) memory_zero_alloc(sizeof(real_t) * equalizer_state->tabsize * equalizer_state->channel_count);
    equalizer_state->lires2 = (real_t *) memory_zero_alloc(sizeof(real_t) * equalizer_state->tabsize * equalizer_state->channel_count);
    equalizer_state->irest = (real_t *) memory_zero_alloc(sizeof(real_t) * equalizer_state->tabsize);
    equalizer_state->fsamples = (real_t *) memory_zero_alloc(sizeof(real_t) * equalizer_state->tabsize);
    equalizer_state->inbuf = (int16_t *) memory_zero_alloc(equalizer_state->winlen * equalizer_state->channel_count * sizeof(int));
    equalizer_state->outbuf = (real_t *) memory_zero_alloc(equalizer_state->tabsize * equalizer_state->channel_count * sizeof(real_t));
    equalizer_state->ditherbuf = (real_t *) memory_zero_alloc(sizeof(real_t) * DITHERLEN);
    // TODO: error checking..

    equalizer_state->lires = equalizer_state->lires1;
    equalizer_state->cur_ires = 1;
    equalizer_state->chg_ires = 1;

    for(fact_index = 0 ; fact_index < DITHERLEN ; fact_index++) {
        equalizer_state->ditherbuf[fact_index] = (((float)rand()) / RAND_MAX - 0.5);
    }

    for(fact_index = 0; fact_index <= M; fact_index++) {
        fact[fact_index] = 1;
        for(fact_pow_index = 1; fact_pow_index <= fact_index; fact_pow_index++) {
            fact[fact_index] *= fact_pow_index;
        }
    }

    iza = izero(alpha(aa));

    equalizer_state->ditherptr = 0;
    equalizer_state->dither = 0;
}

void equalizer_impl_process_param(real_t *bc, equalizer_param_s * band_list, equalizer_param_s ** band_list2, real_t fs, int channel_count) {
    equalizer_param_s *e, *p, *new_band;
    size_t i;

    for (e = band_list ; e != NULL ; ) {
        equalizer_param_s * next = e->next;
        memory_free(e);
        e = next;
    }

    for(i = 0 ; i <= NBANDS ; i++) {
        equalizer_param_s * new_param = memory_zero_alloc(sizeof(*new_param));
        equalizer_param_create(new_param,
            i == 0 ?  0 : bands[i - 1],
            i == NBANDS - 1 ? fs : bands[i],
            bc[i]);

        *band_list2 = new_param;
        band_list2 = &(new_param->next);
    }

    for(e = band_list ; e != NULL ; e = e->next) {
        if ((channel_count == 0 && !e->left) || (channel_count == 1 && !e->right)) {
            continue;
        }

        if (e->lower >= e->upper) {
            continue;
        }

        for(p = *band_list2 ; p != NULL ; p = p->next) {
            if (p->upper > e->lower) {
                break;
            }
        }

        while(p != NULL && p->lower < e->upper) {
            if (e->lower <= p->lower && p->upper <= e->upper) {
                p->gain *= pow(10,e->gain/20);
                p = p->next;
                continue;
            }
            if (p->lower < e->lower && e->upper < p->upper) {
                new_band = memory_zero_alloc(sizeof(*new_band));
                equalizer_param_create(new_band, e->upper, p->upper, p->gain);
                equalizer_param_link(p, new_band);

                new_band = memory_zero_alloc(sizeof(*new_band));
                equalizer_param_create(new_band, e->lower, e->upper, p->gain * pow(10,e->gain/20));
                equalizer_param_link(p, new_band);

                p->upper  = e->lower;

                p = p->next->next->next;
                continue;
            }
            if (p->lower < e->lower) {
                new_band = memory_zero_alloc(sizeof(*new_band));
                equalizer_param_create(new_band, e->lower, p->upper, p->gain * pow(10, e->gain / 20));
                equalizer_param_link(p, new_band);

                p->upper  = e->lower;
                p = p->next->next;
                continue;
            }
            if (e->upper < p->upper) {
                new_band = memory_zero_alloc(sizeof(*new_band));
                equalizer_param_create(new_band, e->upper, p->upper, p->gain);
                equalizer_param_link(p, new_band);

                p->upper  = e->upper;
                p->gain   = p->gain * pow(10,e->gain/20);
                p = p->next->next;
                continue;
            }
            abort();
        }
    }
}

void equalizer_impl_prepare_table(equalizer_state_s * equalizer_state, real_t *lbc, real_t fs) {
    int i,cires = equalizer_state->cur_ires;
    int ch;
    real_t *nires;

    if (fs <= 0) {
        return;
    }

    equalizer_param_s * band_list2;

    for (ch = 0 ; ch < equalizer_state->channel_count ; ch++) {
        equalizer_impl_process_param(lbc, equalizer_state->band_list, &band_list2, fs, 0);

        for(i = 0 ; i < equalizer_state->winlen ; i++) {
            equalizer_state->irest[i] = hn(i - equalizer_state->winlen / 2, band_list2, fs) * win(i - equalizer_state->winlen / 2, equalizer_state->winlen);
        }

        for( ; i < equalizer_state->tabsize ; i++) {
            equalizer_state->irest[i] = 0;
        }

        rfft(equalizer_state->tabsize, 1, equalizer_state->irest);

        nires = cires == 1 ? equalizer_state->lires2 : equalizer_state->lires1;
        nires += ch * equalizer_state->tabsize;

        for(i = 0 ; i < equalizer_state->tabsize ; i++) {
            nires[i] = equalizer_state->irest[i];
        }
    }

    equalizer_state->chg_ires = cires == 1 ? 2 : 1;
}

void equalizer_impl_uninit(equalizer_state_s * equalizer_state) {
    equalizer_state->lires1 = memory_free(equalizer_state->lires1);
    equalizer_state->lires2 = memory_free(equalizer_state->lires2);
    equalizer_state->irest = memory_free(equalizer_state->irest);
    equalizer_state->fsamples = memory_free(equalizer_state->fsamples);
    equalizer_state->inbuf = memory_free(equalizer_state->inbuf);
    equalizer_state->outbuf = memory_free(equalizer_state->outbuf);

    pthread_mutex_destroy(&equalizer_state->table_lock);

    rfft(0, 0, NULL);
}

void equalizer_impl_clear_buffer(equalizer_state_s * equalizer_state) {
	size_t i;

	equalizer_state->nbufsamples = 0;
	for(i = 0; i < equalizer_state->tabsize * equalizer_state->channel_count ; i++) {
	    equalizer_state->outbuf[i] = 0;
	}
}

int equalizer_impl_process(equalizer_state_s * equalizer_state, char *buf,int nsamples,int nch,int bps) {
//    LOG_INFO(LOG_TAG, "equ_process: equalizer_state = 0x%8.8x, nsamples=%i,nch=%i,bps=%i", (int) equalizer_state, nsamples, nch, bps);
    int i,p,ch;
    real_t *ires;
    int amax = (1 << (bps - 1)) - 1;
    int amin = -(1 << (bps - 1));
    static float hm1 = 0;

    if (equalizer_state->chg_ires) {
        equalizer_state->cur_ires = equalizer_state->chg_ires;
        equalizer_state->lires = equalizer_state->cur_ires == 1 ? equalizer_state->lires1 : equalizer_state->lires2;
        equalizer_state->chg_ires = 0;
    }

    p = 0;
    while(equalizer_state->nbufsamples + nsamples >= equalizer_state->winlen) {
        for(i = 0 ; i < (equalizer_state->winlen - equalizer_state->nbufsamples) * nch ; i++) {
            equalizer_state->inbuf[equalizer_state->nbufsamples * nch + i] = ((short *)buf)[i + p * nch];
            float s = equalizer_state->outbuf[equalizer_state->nbufsamples*nch+i];

            if (equalizer_state->dither) {
                float u;
                s -= hm1;
                u = s;
                s += equalizer_state->ditherbuf[(equalizer_state->ditherptr++) & (DITHERLEN-1)];
                if (s < amin) s = amin;
                if (amax < s) s = amax;
                s = RINT(s);
                hm1 = s - u;
                ((short *)buf)[i + p * nch] = s;
            } else {
                if (s < amin) s = amin;
                if (amax < s) s = amax;
                ((short *)buf)[i + p * nch] = RINT(s);
            }
        }

        for(i = equalizer_state->winlen * nch ; i < equalizer_state->tabsize * nch ; i++) {
            equalizer_state->outbuf[i - equalizer_state->winlen * nch] = equalizer_state->outbuf[i];
        }

        p += equalizer_state->winlen - equalizer_state->nbufsamples;
        nsamples -= equalizer_state->winlen - equalizer_state->nbufsamples;
        equalizer_state->nbufsamples = 0;

        for(ch = 0; ch < nch ; ch++) {
            ires = equalizer_state->lires + ch * equalizer_state->tabsize;

            for(i=0;i<equalizer_state->winlen;i++)
                equalizer_state->fsamples[i] = equalizer_state->inbuf[nch*i+ch];

            for(i=equalizer_state->winlen;i<equalizer_state->tabsize;i++)
                equalizer_state->fsamples[i] = 0;

            rfft(equalizer_state->tabsize, 1, equalizer_state->fsamples);

            equalizer_state->fsamples[0] = ires[0]*equalizer_state->fsamples[0];
            equalizer_state->fsamples[1] = ires[1]*equalizer_state->fsamples[1];

            for(i=1;i<equalizer_state->tabsize/2;i++) {
                real_t re,im;

                re = ires[i*2  ]*equalizer_state->fsamples[i*2] - ires[i*2+1]*equalizer_state->fsamples[i*2+1];
                im = ires[i*2+1]*equalizer_state->fsamples[i*2] + ires[i*2  ]*equalizer_state->fsamples[i*2+1];

                equalizer_state->fsamples[i*2  ] = re;
                equalizer_state->fsamples[i*2+1] = im;
            }

            rfft(equalizer_state->tabsize,-1,equalizer_state->fsamples);

            for(i=0;i<equalizer_state->winlen;i++) {
                equalizer_state->outbuf[i*nch+ch] += equalizer_state->fsamples[i]/equalizer_state->tabsize*2;
            }

            for(i=equalizer_state->winlen;i<equalizer_state->tabsize;i++) {
                equalizer_state->outbuf[i*nch+ch] = equalizer_state->fsamples[i]/equalizer_state->tabsize*2;
            }
        }
    }

    for(i=0;i<nsamples*nch;i++) {
        equalizer_state->inbuf[equalizer_state->nbufsamples*nch+i] = ((int16_t *)buf)[i+p*nch];
        float s = equalizer_state->outbuf[equalizer_state->nbufsamples*nch+i];
        if (equalizer_state->dither) {
            float u;
            s -= hm1;
            u = s;
            s += equalizer_state->ditherbuf[(equalizer_state->ditherptr++) & (DITHERLEN-1)];
            if (s < amin) s = amin;
            if (amax < s) s = amax;
            s = RINT(s);
            hm1 = s - u;
            ((short *)buf)[i + p * nch] = s;
        }
        else {
            if (s < amin) s = amin;
            if (amax < s) s = amax;
            ((short *)buf)[i + p * nch] = RINT(s);
        }
    }

    p += nsamples;
    equalizer_state->nbufsamples += nsamples;

    return p;
}

int equalizer_impl_apply_properties(engine_processor_s * processor) {
	int i, ch;

	for (ch = 0 ; ch < 2 ; ch++) {
        float preamp = pow(10, (GAIN_HALF - slider_positions[ch * 11]) / -20.0);
        //LOG_INFO(LOG_TAG, "equalizer_impl_apply_properties: sl[%i] : preamp = %e", ch * 19, preamp);

        for(i = 0 ; i < 10 ; i++) {
            band_values[i + ch * 10] = preamp * pow(10, (GAIN_HALF - slider_positions[1 + i + ch * 11]) / -20.0);
            //LOG_INFO(LOG_TAG, "equalizer_impl_apply_properties: sl[%i] : band[%i] = %e (10^((14 - %i)/-20)",
            //        1 + i + ch * 19,
            //        i + ch * 18,
            //        band_values[i + ch * 18],
            //        slider_positions[1 + i + ch * 19]);
        }
	}

	equalizer_impl_prepare_table(processor->context, band_values, processor->engine->sampling_rate);
	return ENGINE_OK;
}



/*
    Public interface
 */
int equalizer_new(engine_processor_s * processor) {
    if (processor->engine->sample_format == SAMPLE_FORMAT_S16_LE || processor->engine->sample_format == SAMPLE_FORMAT_S16_BE) {
        processor->context = memory_zero_alloc(sizeof(equalizer_state_s));

        equalizer_impl_init(processor->context, 14, 2);
        equalizer_impl_prepare_table(processor->context, band_values, processor->engine->sampling_rate);
        equalizer_impl_clear_buffer(processor->context);

        equalizer_impl_apply_properties(processor);

        return ENGINE_OK;
    }

    return ENGINE_GENERIC_ERROR;
}

int equalizer_delete(engine_processor_s * processor) {
	equalizer_impl_uninit(processor->context);

    return ENGINE_OK;
}

char * equalizer_get_name(engine_context_s * engine) {
    return "10-Band Equalizer";
}

int equalizer_set_property(engine_processor_s * processor, int property, void *value) {
    LOG_INFO(LOG_TAG, "property set [%i] = %i", property, *((int *)value));
    slider_positions[property] = *((int *)value);

    return ENGINE_OK;
}

int equalizer_get_property(engine_processor_s * processor, int property, void * value) {
    LOG_INFO(LOG_TAG, "property get [%i] = %i", property, *((int *)value));

    *((int *) value) = slider_positions[property];
    return ENGINE_OK;
}

int equalizer_process(engine_processor_s * processor, void * data_buffer, size_t data_length) {
    equalizer_state_s * equalizer_state = (equalizer_state_s *)processor->context;

    pthread_mutex_lock(&equalizer_state->table_lock);
        equalizer_impl_process(processor->context, (char *)data_buffer, data_length/4, processor->engine->channel_count, 16);
    pthread_mutex_unlock(&equalizer_state->table_lock);

    return ENGINE_OK;
}

int equalizer_apply_properties(engine_processor_s * processor) {
    equalizer_state_s * equalizer_state = (equalizer_state_s *)processor->context;

    pthread_mutex_lock(&equalizer_state->table_lock);
        equalizer_impl_apply_properties(processor);
        equalizer_impl_prepare_table(processor->context, band_values, processor->engine->sampling_rate);
    pthread_mutex_unlock(&equalizer_state->table_lock);

    return ENGINE_OK;
}

int equalizer_clear(engine_processor_s * processor) {
    LOG_INFO(LOG_TAG, "equalizer_clear()");
    equalizer_impl_clear_buffer(processor->context);
    return ENGINE_OK;
}

static engine_processor_s equalizer_processor = {
	.create = equalizer_new,
	.destroy = equalizer_delete,
	.get_name = equalizer_get_name,

    .set_property = equalizer_set_property,
    .get_property = equalizer_get_property,
    .apply_properties = equalizer_apply_properties,

    .process = equalizer_process,
    .clear = equalizer_clear
};

engine_processor_s * get_equalizer_processor() {
    return &equalizer_processor;
}
