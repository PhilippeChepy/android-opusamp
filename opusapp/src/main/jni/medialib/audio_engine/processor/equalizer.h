/*
 * equalizer.h
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

#ifndef H_EQUALIZER
#define H_EQUALIZER

#ifdef __cplusplus
extern "C" {
#endif

#include <audio_engine/engine.h>

enum equalizer_property_e {
    EQUALIZER_LPREAMP_GAIN = 0,
    EQUALIZER_L1_GAIN = 1,
    EQUALIZER_L2_GAIN = 2,
    EQUALIZER_L3_GAIN = 3,
    EQUALIZER_L4_GAIN = 4,
    EQUALIZER_L5_GAIN = 5,
    EQUALIZER_L6_GAIN = 6,
    EQUALIZER_L7_GAIN = 7,
    EQUALIZER_L8_GAIN = 8,
    EQUALIZER_L9_GAIN = 9,
    EQUALIZER_L10_GAIN = 10,
    EQUALIZER_RPREAMP_GAIN = 11,
    EQUALIZER_R1_GAIN = 12,
    EQUALIZER_R2_GAIN = 13,
    EQUALIZER_R3_GAIN = 14,
    EQUALIZER_R4_GAIN = 15,
    EQUALIZER_R5_GAIN = 16,
    EQUALIZER_R6_GAIN = 17,
    EQUALIZER_R7_GAIN = 18,
    EQUALIZER_R8_GAIN = 19,
    EQUALIZER_R9_GAIN = 20,
    EQUALIZER_R10_GAIN = 21
};

engine_processor_s * get_equalizer_processor();

#ifdef __cplusplus
}
#endif


#endif /* H_EQUALIZER */
