/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api

import com.datadog.android.core.internal.logger.SdkInternalLogger
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.lint.InternalApi
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A Logger used to log messages from the internal implementation of the Datadog SDKs.
 *
 * Rule of thumb to decide which level and target we're using for the Internal Logger usage:
 *
 * - Target.USER: the message needs to either be actionable or provide information about the main
 *     steps in data processing (tracking, storage, upload).
 *     - Level.ERROR: for any actionable error originated from a user's configuration, preventing
 *         a feature from working, or for an issue resulting in unexpected data loss;
 *     - Level.WARN: to inform of an actionable misconfiguration or missuses of the SDK, resulting
 *         in delayed or incomplete data;
 *     - Level.INFO: information about important expected event (e.g.: successful upload);
 * - Target.TELEMETRY: any event that need to be tracked for usage monitoring or for error
 *     diagnostic.
 *     - Level.ERROR, Level.WARN: for any critical error that is unexpected enough and actionable;
 *     - Level.INFO, Level.DEBUG, Level.VERBOSE: important information about critical parts of the
 *         SDK we want to monitor;
 * - Target.MAINTAINER: can be anything relevant about the moving parts of the core SDK or any
 *     of the feature. Level is left to the discretion of the authors of a log.
 *     - Level.ERROR: for any caught error or situation preventing the SDK from working as expected;
 *     - Level.WARN: for any unexpected situation (e.g.: when one would use an IllegalStateException);
 *     - Level.INFO: information about internal high level steps of the SDK core or features;
 *     - Level.DEBUG: information about internal low level steps of the SDK core or features;
 *     - Level.VERBOSE: information on currently debugged feature or open ticket;
 *
 */
@NoOpImplementation
interface InternalLogger {

    /**
     * The severity level of a logged message.
     */
    enum class Level {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    /**
     * The target handler for a log message.
     */
    enum class Target {
        USER,
        MAINTAINER,
        TELEMETRY
    }

    /**
     * Logs a message from the internal implementation.
     * @param level the severity level of the log
     * @param target the target handler for the log
     * @param messageBuilder the lambda building the log message
     * @param throwable an optional throwable error
     * @param onlyOnce whether only one instance of the message should be sent per lifetime of the
     * logger (default is `false`)
     * @param additionalProperties additional properties to add to the log
     */
    fun log(
        level: Level,
        target: Target,
        messageBuilder: () -> String,
        throwable: Throwable? = null,
        onlyOnce: Boolean = false,
        additionalProperties: Map<String, Any?>? = null
    )

    /**
     * Logs a message from the internal implementation.
     * @param level the severity level of the log
     * @param targets list of the target handlers for the log
     * @param messageBuilder the lambda building the log message
     * @param throwable an optional throwable error
     * @param onlyOnce whether only one instance of the message should be sent per lifetime of the
     * logger (default is `false`, onlyOnce applies to each target independently)
     * @param additionalProperties additional properties to add to the log
     */
    fun log(
        level: Level,
        targets: List<Target>,
        messageBuilder: () -> String,
        throwable: Throwable? = null,
        onlyOnce: Boolean = false,
        additionalProperties: Map<String, Any?>? = null
    )

    /**
     * Logs a specific metric from the internal implementation. The metric values will be sent
     * as key-value pairs in the additionalProperties and as part of the
     * [com.datadog.android.telemetry.model.TelemetryDebugEvent.Telemetry] event.
     * @param messageBuilder the lambda building the metric message
     * @param additionalProperties additional properties to add to the metric
     */
    @InternalApi
    fun logMetric(messageBuilder: () -> String, additionalProperties: Map<String, Any?>)

    /**
     * Start measuring a performance metric.
     *
     * @param callerClass  name of the class calling the performance measurement.
     * @param metric name of the metric that we want to measure.
     * @param samplingRate value between 0-100 for sampling the event.
     * @param operationName the name of the operation being measured
     * @return a PerformanceMetric object that can later be used to send telemetry, or null if sampled out
     */
    @InternalApi
    fun startPerformanceMeasure(
        callerClass: String,
        metric: TelemetryMetricType,
        samplingRate: Float,
        operationName: String
    ): PerformanceMetric?

    companion object {

        /**
         * Logger for the cases when SDK instance is not yet available. Try to use the logger
         * provided by [FeatureSdkCore.internalLogger] instead if possible.
         */
        val UNBOUND: InternalLogger = SdkInternalLogger(null)
    }
}
