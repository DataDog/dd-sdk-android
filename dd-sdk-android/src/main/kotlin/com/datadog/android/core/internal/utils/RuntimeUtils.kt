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
import com.datadog.android.monitoring.internal.InternalLogsFeature
import java.util.Locale

internal const val SDK_LOG_PREFIX = "DD_LOG"
internal const val SDK_LOGGER_NAME = "sdkLogger"
internal const val DEV_LOG_PREFIX = "Datadog"

/**
 * Global SDK Logger. This logger is meant for internal debugging purposes.
 * Logcat logs are conditioned by a BuildConfig flag (set to false for releases).
 * Datadog logs are conditioned by the [InternalLogsFeature].
 */
internal var sdkLogger: Logger = buildSdkLogger()
    private set

internal fun rebuildSdkLogger() {
    sdkLogger = buildSdkLogger()
}

internal fun buildSdkLogger(): Logger {
    return Logger.Builder()
        .setLogcatLogsEnabled(BuildConfig.LOGCAT_ENABLED)
        .setServiceName(SDK_LOG_PREFIX)
        .setLoggerName(SDK_LOGGER_NAME)
        .setBundleWithRumEnabled(false)
        .setBundleWithTraceEnabled(false)
        .setInternal(true)
        .build()
}

/**
 * Global Dev Logger. This logger is meant for user's debugging purposes.
 * Logcat logs are conditioned by the [Datadog.libraryVerbosity].
 * No Datadog logs should be sent.
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

/**
 * Warns the user that they're using a deprecated feature.
 * @param target the target feature (e.g. method name)
 * @param deprecatedSince the version when the feature was deprecated
 * @param removedInVersion the version in which the feature will disappear
 * @param alternative an alternative option to get the same effect
 */
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
