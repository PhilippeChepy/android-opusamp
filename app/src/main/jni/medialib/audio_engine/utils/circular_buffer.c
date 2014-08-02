/*
 * circular_buffer.c
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

#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include <audio_engine/utils/circular_buffer.h>
#include <audio_engine/utils/memory.h>
#include <audio_engine/utils/log.h>

#define LOG_TAG "(jni).utils.circular_buffer"

int circular_buffer_new(circular_buffer_s * circular_buffer, size_t capacity) {
    int error_code = CIRCULAR_BUFFER_GENERIC_ERROR;
    assert(circular_buffer != NULL);

    memset(circular_buffer, 0, sizeof *circular_buffer);
    circular_buffer->buffer = memory_alloc(capacity);
    if (circular_buffer->buffer == NULL) {
    	LOG_INFO(LOG_TAG, "circular_buffer_new: CIRCULAR_BUFFER_ERROR_ALLOCATING.");
        error_code = CIRCULAR_BUFFER_ERROR_ALLOCATING;
    }
    else {
		LOG_INFO(LOG_TAG, "circular_buffer_new: OK.");
		circular_buffer->capacity = capacity;
		circular_buffer->head_index = 0;
		circular_buffer->tail_index = 0;
		circular_buffer->used = 0;
		error_code = CIRCULAR_BUFFER_OK;
    }

    return error_code;
}

int circular_buffer_delete(circular_buffer_s * circular_buffer) {
    assert(circular_buffer != NULL);

    if (circular_buffer->buffer != NULL) {
        memory_free(circular_buffer->buffer);
    }

    memset(circular_buffer, 0, sizeof *circular_buffer);
    return CIRCULAR_BUFFER_OK;
}

int circular_buffer_read(circular_buffer_s * circular_buffer, void * buffer, size_t * length) {
    assert(circular_buffer != NULL && buffer != NULL && length != NULL);

    if (circular_buffer->used == 0) {
        *length = 0;
    }
    else {
		if (*length > circular_buffer->used) {
			*length = circular_buffer->used;
		}

		if (circular_buffer->head_index < circular_buffer->tail_index ||
				circular_buffer->head_index + *length <= circular_buffer->capacity) {
			/*
			 * --.++----.--             --.------.++--       -- length=3
			 *   ^head  ^tail    |        ^tail  ^head
			 */
			char * src_buffer = (char *)circular_buffer->buffer;
			memcpy(buffer, &(src_buffer[circular_buffer->head_index]), *length);
		}
		else {
			/*
			 * ++--.-----.+ h=10   -- length=4
			 *     ^tail ^head
			 */
			size_t first_part_length = circular_buffer->capacity - circular_buffer->head_index;
			char * src_buffer = (char *)circular_buffer->buffer;
			char * dst_buffer = (char *)buffer;

			memcpy(buffer, &(src_buffer[circular_buffer->head_index]), first_part_length);
			memcpy(&(dst_buffer[first_part_length]), circular_buffer->buffer, *length - first_part_length);
		}

		circular_buffer->head_index = (circular_buffer->head_index + *length) % circular_buffer->capacity;
		circular_buffer->used = (circular_buffer->used - *length);

		//DBG: LOG_INFO(LOG_TAG, "circular_buffer_read: used=%i, capacity=%i.", circular_buffer->used, circular_buffer->capacity);
    }

    return CIRCULAR_BUFFER_OK;
}

int circular_buffer_write(circular_buffer_s * circular_buffer, void * buffer, size_t * length) {
    assert(circular_buffer != NULL && buffer != NULL && length != NULL);

    if (circular_buffer->used == circular_buffer->capacity) {
        *length = 0;
    }
    else {
		if (*length > circular_buffer->capacity - circular_buffer->used) {
			*length = circular_buffer->capacity - circular_buffer->used;
		}

		if (circular_buffer->tail_index < circular_buffer->head_index ||
				circular_buffer->tail_index + *length <= circular_buffer->capacity) {
			/*
			 * --.++----.--             --.------.++--       -- length=3
			 *   ^tail  ^head    |        ^head  ^tail
			 */
			char * dst_buffer = (char *)circular_buffer->buffer;
			memcpy(&(dst_buffer[circular_buffer->tail_index]), buffer, *length);
		}
		else {
			/*
			 * ++--.-----.+ h=10   -- length=4
			 *     ^tail ^head
			 */
			size_t first_part_length = circular_buffer->capacity - circular_buffer->tail_index;
			char * dst_buffer = (char *)circular_buffer->buffer;
			char * src_buffer = (char *)buffer;
			memcpy(&(dst_buffer[circular_buffer->tail_index]), src_buffer, first_part_length);
			memcpy(dst_buffer, &(src_buffer[first_part_length]), *length - first_part_length);
		}

		circular_buffer->tail_index = (circular_buffer->tail_index + *length) % circular_buffer->capacity;
		circular_buffer->used = (circular_buffer->used + *length);

		//DBG: LOG_INFO(LOG_TAG, "circular_buffer_write: used=%i, capacity=%i.", circular_buffer->used, circular_buffer->capacity);
    }

    return CIRCULAR_BUFFER_OK;
}

int circular_buffer_clear(circular_buffer_s * circular_buffer) {
	circular_buffer->head_index = 0;
	circular_buffer->tail_index = 0;
	circular_buffer->used = 0;

    return CIRCULAR_BUFFER_OK;
}
