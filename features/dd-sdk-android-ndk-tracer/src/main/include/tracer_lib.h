#ifndef TRACER_LIB_H
#define TRACER_LIB_H

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif
/**
 * Opaque handle representing a tracer instance
 */
typedef struct Tracer Tracer;

/**
 * Opaque handle representing a span ID
 */
typedef uint64_t SpanId;

/**
 * Creates a new tracer instance
 * @return Pointer to the new tracer instance
 */
Tracer* tracer_new(void);

/**
 * Destroys a tracer instance and frees associated resources
 * @param tracer Pointer to the tracer instance
 */
void tracer_free(Tracer *tracer);

/**
 * Starts a new span with the given name
 * @param tracer Pointer to the tracer instance
 * @param name Name of the span
 * @param parent_id Optional parent span ID (0 for no parent)
 * @return ID of the created span
 */
SpanId tracer_start_span(Tracer *tracer, const char *name, SpanId parent_id);

/**
 * Ends a span with the given ID
 * @param tracer Pointer to the tracer instance
 * @param span_id ID of the span to end
 * @return true if successful, false if span not found
 */
bool tracer_end_span(Tracer *tracer, SpanId span_id);

/**
 * Sets an attribute on a span
 * @param tracer Pointer to the tracer instance
 * @param span_id ID of the target span
 * @param key Attribute key
 * @param value Attribute value
 * @return true if successful, false if span not found
 */
bool tracer_set_attribute(Tracer *tracer,
                          SpanId span_id,
                          const char *key,
                          const char *value);

/**
 * Gets the current active span ID
 * @param tracer Pointer to the tracer instance
 * @return Current active span ID, or 0 if no active span
 */
SpanId tracer_get_active_span(const Tracer *tracer);




#ifdef __cplusplus
}
#endif

#endif  /* TRACER_LIB_H */
