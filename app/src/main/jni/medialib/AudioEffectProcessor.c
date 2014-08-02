#include <jni.h>

#include <audio_processor/processor.h>
#include <audio_processor/utils/log.h>
#include <audio_processor/utils/memory.h>

#define LOG_TAG "AudioEngineProcessor-JNI"

static long ptr_to_id(void * ptr) {
	return (long) ptr;
}

static void * id_to_ptr(long id) {
	return (void *) id;
}

