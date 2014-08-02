/*
 * circular_buffer.h
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

#ifndef H_CIRCULAR_BUFFER
#define H_CIRCULAR_BUFFER

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>

typedef struct {
    void * buffer;
    size_t capacity;
    size_t head_index;
    size_t tail_index;
    size_t used;
} circular_buffer_s;

enum circular_buffer_error_code {
    CIRCULAR_BUFFER_OK = 0,
    CIRCULAR_BUFFER_GENERIC_ERROR = -1,
    CIRCULAR_BUFFER_ERROR_ALLOCATING = -2,
};

int circular_buffer_new(circular_buffer_s * circular_buffer, size_t capacity);
int circular_buffer_delete(circular_buffer_s * circular_buffer);
int circular_buffer_read(circular_buffer_s * circular_buffer, void * buffer, size_t * length);
int circular_buffer_write(circular_buffer_s * circular_buffer, void * buffer, size_t * length);
int circular_buffer_clear(circular_buffer_s * circular_buffer);

#ifdef __cplusplus
}
#endif

#endif
