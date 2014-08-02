/*
 * audiotrack.h
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

#ifndef H_OUTPUTS_AUDIOTRACK
#define H_OUTPUTS_AUDIOTRACK

#ifdef __cplusplus
extern "C" {
#endif

#include <audio_engine/engine.h>

engine_output_s const * audiotrack_get_output();

#ifdef __cplusplus
}
#endif


#endif /* H_OUTPUTS_AUDIOTRACK */
