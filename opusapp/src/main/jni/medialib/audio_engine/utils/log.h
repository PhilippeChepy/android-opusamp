/*
 * log.h
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

#ifndef H_LOG
#define H_LOG

#include <android/log.h>

#define LOG_ERROR(log_tag, ...)   {__android_log_print(ANDROID_LOG_ERROR, log_tag, __VA_ARGS__); }
#define LOG_WARNING(log_tag, ...) {__android_log_print(ANDROID_LOG_ERROR, log_tag, __VA_ARGS__); }
#define LOG_DEBUG(log_tag, ...)   {__android_log_print(ANDROID_LOG_DEBUG, log_tag, __VA_ARGS__);}
#define LOG_INFO(log_tag, ...)    {__android_log_print(ANDROID_LOG_INFO, log_tag, __VA_ARGS__);}

#endif /* H_LOG */
