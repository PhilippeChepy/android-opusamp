#ifdef ENABLE_PUREC_FLOAT
#undef ENABLE_PUREC_FLOAT
#endif

#ifdef ENABLE_AVX_FLOAT
#undef ENABLE_AVX_FLOAT
#endif



#ifndef ENABLE_SSE_FLOAT
#define ENABLE_SSE_FLOAT
#endif

#include <audio_engine/processor/fft/dft_undiff.c>
#include <audio_engine/processor/fft/simd_base_undiff.c>
