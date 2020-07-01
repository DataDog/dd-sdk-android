#ifndef DATETIME_H
#define DATETIME_H

#include "cstdint"

#ifdef __cplusplus
extern "C" {
#endif

uint64_t time_since_epoch();


void format_date(char *buffer, size_t buffer_size, const char *format);

#ifdef __cplusplus
}
#endif
#endif