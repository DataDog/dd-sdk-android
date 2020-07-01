
#include <jni.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Monitor for fatal signals, updating the crash context when detected, the
 * serialize to disk and invoke the previously-installed handler
 * @return true if monitoring started successfully
 */
bool installSignalHandlers(JNIEnv *env);
/**
 * Stop monitoring for fatal exceptions and reinstall previously-installed
 * handlers
 */
void uninstallSignalHandlers(void);

#ifdef __cplusplus
}
#endif