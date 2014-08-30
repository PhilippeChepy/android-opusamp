/*
 * memory.h
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

#ifndef H_MEMORY
#define H_MEMORY

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#include <stdlib.h>
#include <string.h>

extern unsigned long memory_alloc_counter;

void * memory_alloc(size_t size);
void * memory_realloc(void * ptr, size_t newsize);
void * memory_zero_alloc(size_t size);
void * memory_free(void * ptr);

#define memory_zero(dest, len) memset(dest, 0, len)
void * memory_clone(const void * source, size_t size);
int memory_compare(const void * source1, const void * source2, size_t size);
#define memory_copy(dest, src, len)     memcpy(dest, src, len)

void * memory_aligned_alloc(size_t alignment, size_t bytes);
void memory_aligned_free(void *raw_data);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* H_MEMORY */
