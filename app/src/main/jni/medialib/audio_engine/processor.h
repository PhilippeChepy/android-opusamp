#ifndef H_PROCESSOR
#define H_PROCESSOR

#include <stdint.h>

enum engine_result_code_e {
	ENGINE_OK = 0,
	ENGINE_GENERIC_ERROR = -1,
	ENGINE_INVALID_FORMAT_ERROR = -2,
	ENGINE_INVALID_PARAMETER_ERROR = -3,
	ENGINE_ALLOC_ERROR = -4,
	ENGINE_FILE_ACCESS_ERROR = -5,
};

typedef struct {
    int sampling_rate;
    int channel_count;
    int is_pass_through;

    void * processor_specific;
} processor_context_s;


typedef struct {
	int (* processor_new)(processor_context_s * processor_context, int sampling_rate, int channel_count);
	int (* processor_delete)(processor_context_s * processor_context);
	int (* processor_get_name)(processor_context_s * processor_context, char ** input_name);

    int (* processor_set_property)(processor_context_s * processor_context, int property_key, void * property_data);
	int (* processor_process)(processor_context_s * processor_context, uint8_t * data, size_t data_length);
} processor_s;

#endif /* H_PROCESSOR */
