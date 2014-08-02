/*
 * memory.c
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

#include <string.h>

#include  <audio_engine/utils/memory.h>

unsigned long memory_alloc_counter = 0;

void * memory_alloc(size_t size) {
	void * ptr = malloc(size);

	if (ptr) {
		memory_alloc_counter++;
	}
	return ptr;
}

void * memory_zero_alloc(size_t size) {
	void * ptr = calloc(1, size);

	if (ptr) {
		memory_alloc_counter++;
	}
	return ptr;
}

void * memory_free(void * ptr) {
	if (ptr) {
		memory_alloc_counter--;
		free(ptr);
	}
	return NULL;
}

void * memory_clone(const void * source, size_t source_length) {
	void * target = malloc(source_length);
	if (target) {
		memory_alloc_counter++;
		memmove(target, source, source_length);
	}
	return target;
}

int memory_compare(const void * source1, const void * source2, size_t size) {
	return memcmp(source1, source2, size);
}

