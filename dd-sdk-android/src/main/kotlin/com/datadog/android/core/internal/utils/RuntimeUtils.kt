/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.ConditionalLogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import java.util.Locale

internal const val SDK_LOG_PREFIX = "DD_LOG"
internal const val DEV_LOG_PREFIX = "Datadog"

/**
 * Global SDK Logger. This logger is meant for internal debugging purposes. Should not post logs
 * to Datadog endpoint and should be conditioned by the BuildConfig flag.
 */
internal val sdkLogger: Logger = buildSdkLogger()

internal fun buildSdkLogger(): Logger {
    val handler = if (BuildConfig.LOGCAT_ENABLED) {
        LogcatLogHandler(SDK_LOG_PREFIX, true)
    } else {
        NoOpLogHandler()
    }
    return Logger(handler)
}

/**
 * Global Dev Logger. This logger is meant for user's debugging purposes. Should not post logs
 * to Datadog endpoint and should be conditioned by the Datadog Verbosity level.
 */
internal val devLogger: Logger = buildDevLogger()

private fun buildDevLogger(): Logger {
    return Logger(buildDevLogHandler())
}

internal fun buildDevLogHandler(): ConditionalLogHandler {
    return ConditionalLogHandler(
        LogcatLogHandler(DEV_LOG_PREFIX, false)
    ) { i, _ ->
        i >= Datadog.libraryVerbosity
    }
}

internal fun warnDeprecated(
    target: String,
    deprecatedSince: String,
    removedInVersion: String,
    alternative: String? = null
) {
    if (alternative == null) {
        devLogger.w(
            WARN_DEPRECATED.format(
                Locale.US,
                target,
                deprecatedSince,
                removedInVersion
            )
        )
    } else {
        devLogger.w(
            WARN_DEPRECATED_WITH_ALT.format(
                Locale.US,
                target,
                deprecatedSince,
                removedInVersion,
                alternative
            )
        )
    }
}

internal const val WARN_DEPRECATED = "%s has been deprecated since version %s, " +
    "and will be removed in version %s."

internal const val WARN_DEPRECATED_WITH_ALT = "%s has been deprecated since version %s, " +
    "and will be removed in version %s. Please use %s instead"
