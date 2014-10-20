#ifndef ENABLE_NEON_FLOAT
#define ENABLE_NEON_FLOAT
#endif

#ifdef ENABLE_PUREC_FLOAT
#undef ENABLE_PUREC_FLOAT
#endif

#include <audio_engine/processor/fft/dft_undiff.c>
#include <audio_engine/processor/fft/simd_base_undiff.c>
