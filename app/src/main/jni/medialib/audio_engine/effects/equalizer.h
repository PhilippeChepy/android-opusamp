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

#include <audio_engine/processor.h>

enum equalizer_property_e { /* gain is in [-24.0f, 12.0f] */
    EQUALIZER_BAND1_GAIN,
    EQUALIZER_BAND2_GAIN,
    EQUALIZER_BAND3_GAIN,
    EQUALIZER_BAND4_GAIN,
    EQUALIZER_BAND5_GAIN,
    EQUALIZER_BAND6_GAIN,
    EQUALIZER_BAND7_GAIN,
    EQUALIZER_BAND8_GAIN,
    EQUALIZER_BAND9_GAIN,
    EQUALIZER_BAND10_GAIN,
};

processor_s const * get_equalizer_processor();

#ifdef __cplusplus
}
#endif


#endif /* H_EQUALIZER */
