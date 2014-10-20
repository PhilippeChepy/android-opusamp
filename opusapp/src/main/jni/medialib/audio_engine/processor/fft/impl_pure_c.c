#ifndef ENABLE_PUREC_FLOAT
#define ENABLE_PUREC_FLOAT
#endif

#ifdef ENABLE_NEON_FLOAT
#undef ENABLE_NEON_FLOAT
#endif

#include <audio_engine/processor/fft/dft_undiff.c>
#include <audio_engine/processor/fft/simd_base_undiff.c>
