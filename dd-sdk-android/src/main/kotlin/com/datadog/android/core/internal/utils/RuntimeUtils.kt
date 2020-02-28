package com.datadog.android.core.internal.utils

import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.ConditionalLogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler

internal const val SDK_LOG_PREFIX = "DD_LOG"
internal const val DEV_LOG_PREFIX = "Datadog"

/**
 * Global SDK Logger. This logger is meant for internal debugging purposes. Should not post logs
 * to Datadog endpoint and should be conditioned by the BuildConfig flag.
 */
internal val sdkLogger: Logger = buildSdkLogger()

internal fun buildSdkLogger(): Logger {
    val handler = if (BuildConfig.LOGCAT_ENABLED) {
        LogcatLogHandler(SDK_LOG_PREFIX)
    } else {
        NoOpLogHandler
    }
    return Logger(handler)
}

/**
 * Global Dev Logger. This logger is meant for user's debugging purposes. Should not post logs
 * to Datadog endpoint and should be conditioned by the Datadog Verbosity level.
 */
internal val devLogger: Logger = buildDevLogger()

private fun buildDevLogger(): Logger {
    val handler = ConditionalLogHandler(
        LogcatLogHandler(DEV_LOG_PREFIX, 1)
    ) { i, _ ->
        i >= Datadog.libraryVerbosity
    }
    return Logger(handler)
}
