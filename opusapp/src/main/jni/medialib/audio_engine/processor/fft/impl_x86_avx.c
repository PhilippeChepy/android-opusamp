#ifdef ENABLE_PUREC_FLOAT
#undef ENABLE_PUREC_FLOAT
#endif

#ifdef ENABLE_SSE_FLOAT
#undef ENABLE_SSE_FLOAT
#endif

#ifndef ENABLE_AVX_FLOAT
#define ENABLE_AVX_FLOAT
#endif

#include <audio_engine/processor/fft/dft_undiff.c>
#include <audio_engine/processor/fft/simd_base_undiff.c>