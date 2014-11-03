/*
 * ffinput.h
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

#ifndef H_FFINPUT
#define H_FFINPUT

#ifdef __cplusplus
extern "C" {
#endif

#include <audio_engine/engine.h>

engine_input_s * ffinput_get_input();

#ifdef __cplusplus
}
#endif


#endif /* H_FFINPUT */
