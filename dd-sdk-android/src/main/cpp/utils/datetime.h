#include "cstdint"


#ifdef __cplusplus
extern "C" {
#endif

uint64_t timeSinceEpochMillisec();


void dateTimeWithFormat(char *buffer, size_t bufferSize, const char *format);

#ifdef __cplusplus
}
#endif