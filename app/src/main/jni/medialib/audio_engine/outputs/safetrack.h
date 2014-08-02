/*
 * safetrack.h
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

#ifndef H_OUTPUTS_SAFETRACK
#define H_OUTPUTS_SAFETRACK

#ifdef __cplusplus
extern "C" {
#endif

#include <audio_engine/engine.h>
#include <jni.h>

engine_output_s const * safetrack_get_output();

void safetrack_set_vm(JavaVM * vm);

#ifdef __cplusplus
}
#endif


#endif /* H_OUTPUTS_SAFETRACK */
