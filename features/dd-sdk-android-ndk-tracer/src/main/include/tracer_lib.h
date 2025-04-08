#ifndef TRACER_LIB_H
#define TRACER_LIB_H

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct Tracer Tracer;

struct Tracer *tracer_new(void);

void tracer_free(struct Tracer *tracer);

char *tracer_start_span(struct Tracer *tracer, const char *name, const char *parent_id);

bool tracer_end_span(struct Tracer *tracer, const char *span_id);

//bool tracer_set_attribute(struct Tracer *tracer,
//                          const char *span_id,
//                          const char *key,
//                          const char *value);
//
//char *tracer_get_active_span(const struct Tracer *tracer);
//
//void tracer_free_string(char *ptr);

#ifdef __cplusplus
}
#endif

#endif  /* TRACER_LIB_H */
