/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import androidx.annotation.FloatRange
import com.datadog.android.Datadog
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import android.util.Log as AndroidLog

/**
 * A class enabling Datadog logging features.
 *
 * It allows you to create a specific context (automatic information, custom attributes, tags) that
 * will be embedded in all logs sent through this logger.
 *
 * You can have multiple loggers configured in your application, each with their own settings.
 */
@Suppress("TooManyFunctions", "MethodOverloading")
class Logger
internal constructor(internal var handler: LogHandler) {

    private val attributes = ConcurrentHashMap<String, Any?>()
    internal val tags = CopyOnWriteArraySet<String>()

    // region Log

    /**
     * Sends a VERBOSE log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun v(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(AndroidLog.VERBOSE, message, throwable, attributes)
    }

    /**
     * Sends a Debug log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun d(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(AndroidLog.DEBUG, message, throwable, attributes)
    }

    /**
     * Sends an Info log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun i(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(AndroidLog.INFO, message, throwable, attributes)
    }

    /**
     * Sends a Warning log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun w(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(AndroidLog.WARN, message, throwable, attributes)
    }

    /**
     * Sends an Error log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun e(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(AndroidLog.ERROR, message, throwable, attributes)
    }

    /**
     * Sends an Assert log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun wtf(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(AndroidLog.ASSERT, message, throwable, attributes)
    }

    /**
     * Sends a log message.
     *
     * @param priority the priority level (must be one of the Android Log.* constants)
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @JvmOverloads
    fun log(
        priority: Int,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(priority, message, throwable, attributes)
    }

    /**
     * Sends a log message with strings for error information.
     *
     * This method is meant for non-native or cross platform frameworks (such as React Native or
     * Flutter) to send error information to Datadog. Although it can be used directly, it is
     * recommended to use other methods declared on `Logger`.
     *
     * @param priority the priority level (must be one of the Android Log.* constants)
     * @param message the message to be logged
     * @param errorKind the kind of error to be logged with the message
     * @param errorMessage the message from the error to be logged with this message
     * @param errorStacktrace the stack trace from the error to be logged with this message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @JvmOverloads
    @Suppress("LongParameterList")
    fun log(
        priority: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStacktrace: String?,
        attributes: Map<String, Any?> = emptyMap()
    ) {
        internalLog(priority, message, errorKind, errorMessage, errorStacktrace, attributes)
    }

    // endregion

    // region Builder

    /**
     * A Builder class for a [Logger].
     *
     * @param sdkCore SDK instance to bind to. If not provided, default instance will be used.
     */
    class Builder @JvmOverloads constructor(sdkCore: SdkCore = Datadog.getInstance()) {

        private val sdkCore: FeatureSdkCore = sdkCore as FeatureSdkCore

        private var serviceName: String? = null
        private var loggerName: String? = null
        private var logcatLogsEnabled: Boolean = false
        private var networkInfoEnabled: Boolean = false
        private var bundleWithTraceEnabled: Boolean = true
        private var bundleWithRumEnabled: Boolean = true
        private var sampleRate: Float = DEFAULT_SAMPLE_RATE
        private var minDatadogLogsPriority: Int = -1

        /**
         * Builds a [Logger] based on the current state of this Builder.
         */
        fun build(): Logger {
            val logsFeature = sdkCore
                .getFeature(Feature.LOGS_FEATURE_NAME)
                ?.unwrap<LogsFeature>()
            val datadogLogsEnabled = sampleRate > 0
            val handler = when {
                datadogLogsEnabled && logcatLogsEnabled -> {
                    CombinedLogHandler(
                        buildDatadogHandler(sdkCore, logsFeature),
                        buildLogcatHandler(sdkCore)
                    )
                }
                datadogLogsEnabled -> buildDatadogHandler(sdkCore, logsFeature)
                logcatLogsEnabled -> buildLogcatHandler(sdkCore)
                else -> NoOpLogHandler()
            }

            return Logger(handler)
        }

        /**
         * Sets the service name that will appear in your logs.
         * @param service the service name (default = application package name)
         */
        fun setService(service: String): Builder {
            this.serviceName = service
            return this
        }

        /**
         * Sets a minimum threshold (priority) for the log to be sent to the Datadog servers. If log priority
         * is below this one, then it won't be sent. Default value is -1 (allow all).
         * @param minLogThreshold Minimum log threshold to be sent to the Datadog servers.
         */
        fun setRemoteLogThreshold(minLogThreshold: Int): Builder {
            minDatadogLogsPriority = minLogThreshold
            return this
        }

        /**
         * Enables your logs to be duplicated in LogCat.
         * @param enabled false by default
         */
        fun setLogcatLogsEnabled(enabled: Boolean): Builder {
            logcatLogsEnabled = enabled
            return this
        }

        /**
         * Enables network information to be automatically added in your logs.
         * @param enabled false by default
         */
        fun setNetworkInfoEnabled(enabled: Boolean): Builder {
            networkInfoEnabled = enabled
            return this
        }

        /**
         * Sets the logger name that will appear in your logs when a throwable is attached.
         * @param name the logger custom name (default = application package name)
         */
        fun setName(name: String): Builder {
            loggerName = name
            return this
        }

        /**
         * Enables the logs bundling with the current active trace. If this feature is enabled all
         * the logs from this moment on will be bundled with the current trace and you will be able
         * to see all the logs sent during a specific trace.
         * @param enabled true by default
         */
        fun setBundleWithTraceEnabled(enabled: Boolean): Builder {
            bundleWithTraceEnabled = enabled
            return this
        }

        /**
         * Enables the logs bundling with the current active View. If this feature is enabled all
         * the logs from this moment on will be bundled with the current view information and you
         * will be able to see all the logs sent during a specific view in the Rum Explorer.
         * @param enabled true by default
         */
        fun setBundleWithRumEnabled(enabled: Boolean): Builder {
            bundleWithRumEnabled = enabled
            return this
        }

        /**
         * Sets the sample rate for this Logger.
         * @param sampleRate the sample rate, in percent.
         * A value of `30` means we'll send 30% of the logs. If value is `0`, no logs will be sent
         * to Datadog.
         * Default is 100.0 (ie: all logs are sent).
         */
        fun setRemoteSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder {
            this.sampleRate = sampleRate
            return this
        }

        // region Internal

        private fun buildLogcatHandler(sdkCore: SdkCore?): LogHandler {
            return LogcatLogHandler(
                serviceName = serviceName ?: sdkCore?.service ?: "unknown",
                useClassnameAsTag = true
            )
        }

        private fun buildDatadogHandler(
            sdkCore: FeatureSdkCore,
            logsFeature: LogsFeature?
        ): LogHandler {
            if (logsFeature == null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { SDK_NOT_INITIALIZED_WARNING_MESSAGE }
                )
                return NoOpLogHandler()
            }

            return DatadogLogHandler(
                sdkCore = sdkCore,
                loggerName = loggerName ?: logsFeature.packageName,
                logGenerator = DatadogLogGenerator(
                    serviceName ?: sdkCore.service
                ),
                writer = logsFeature.dataWriter,
                minLogPriority = minDatadogLogsPriority,
                bundleWithTraces = bundleWithTraceEnabled,
                bundleWithRum = bundleWithRumEnabled,
                sampler = RateBasedSampler(sampleRate),
                attachNetworkInfo = networkInfoEnabled
            )
        }

        // endregion
    }

    // endregion

    // region Context Information (attributes, tags)
    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the attribute value
     */
    fun addAttribute(key: String, value: Any?) {
        if (value == null) {
            attributes[key] = NULL_MAP_VALUE
        } else {
            attributes[key] = value
        }
    }

    /**
     * Remove a custom attribute from all future logs sent by this logger.
     * Previous logs won't lose the attribute value associated with this key if they were created
     * prior to this call.
     * @param key the key of the attribute to remove
     */
    fun removeAttribute(key: String) {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        attributes.remove(key)
    }

    /**
     * Add a tag to all future logs sent by this logger.
     * The tag will take the form "key:value".
     *
     * Tags must start with a letter and after that may contain the following characters:
     * Alphanumerics, Underscores, Minuses, Colons, Periods, Slashes. Other special characters
     * are converted to underscores.
     * Tags must be lowercase, and can be at most 200 characters. If the tag you provide is
     * longer, only the first 200 characters will be used.
     *
     * @param key the key for this tag
     * @param value the (non null) value of this tag
     * @see [documentation](https://docs.datadoghq.com/tagging/#defining-tags)
     */
    fun addTag(key: String, value: String) {
        addTagInternal("$key:$value")
    }

    /**
     * Add a tag to all future logs sent by this logger.
     *
     * Tags must start with a letter and after that may contain the following characters:
     * Alphanumerics, Underscores, Minuses, Colons, Periods, Slashes. Other special characters
     * are converted to underscores.
     * Tags must be lowercase, and can be at most 200 characters. If the tag you provide is
     * longer, only the first 200 characters will be used.
     *
     * @param tag the (non null) tag
     * @see [documentation](https://docs.datadoghq.com/tagging/#defining-tags)
     */
    fun addTag(tag: String) {
        addTagInternal(tag)
    }

    /**
     * Remove a tag from all future logs sent by this logger.
     * Previous logs won't lose the this tag if they were created prior to this call.
     * @param tag the tag to remove
     */
    fun removeTag(tag: String) {
        removeTagInternal(tag)
    }

    /**
     * Remove all tags with the given key from all future logs sent by this logger.
     * Previous logs won't lose the this tag if they were created prior to this call.
     * @param key the key of the tags to remove
     */
    fun removeTagsWithKey(key: String) {
        val prefix = "$key:"
        safelyRemoveTagsWithKey {
            it.startsWith(prefix)
        }
    }

    // endregion

    // region Internal

    internal fun internalLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        localAttributes: Map<String, Any?>,
        timestamp: Long? = null
    ) {
        val combinedAttributes = mutableMapOf<String, Any?>()
        combinedAttributes.putAll(attributes)
        combinedAttributes.putAll(localAttributes)
        // need to make a copy, because the content will be access on another thread and it
        // can change by then
        val tagsSnapshot = HashSet(tags)
        handler.handleLog(level, message, throwable, combinedAttributes, tagsSnapshot, timestamp)
    }

    @Suppress("LongParameterList")
    internal fun internalLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStacktrace: String?,
        localAttributes: Map<String, Any?>,
        timestamp: Long? = null
    ) {
        val combinedAttributes = mutableMapOf<String, Any?>()
        combinedAttributes.putAll(attributes)
        combinedAttributes.putAll(localAttributes)
        // need to make a copy, because the content will be access on another thread and it
        // can change by then
        val tagsSnapshot = HashSet(tags)
        handler.handleLog(
            level,
            message,
            errorKind,
            errorMessage,
            errorStacktrace,
            combinedAttributes,
            tagsSnapshot,
            timestamp
        )
    }

    private fun addTagInternal(tag: String) {
        tags.add(tag)
    }

    private fun removeTagInternal(tag: String) {
        tags.remove(tag)
    }

    private fun safelyRemoveTagsWithKey(keyFilter: (String) -> Boolean) {
        // we first gather all the objects we want to remove based on a copy
        val toRemove: List<String> = tags.toTypedArray().filter(keyFilter)
        @Suppress("UnsafeThirdPartyFunctionCall")
        // NPE cannot happen here (toRemove is explicitly non null)
        // ClassCastException cannot happen, we're removing objects that come from the set
        tags.removeAll(toRemove)
    }

    // endregion

    internal companion object {
        internal const val DEFAULT_SAMPLE_RATE = 100f
        internal const val SDK_NOT_INITIALIZED_WARNING_MESSAGE =
            "You're trying to create a Logger instance, but the SDK was not yet initialized. " +
                "This Logger will not be able to send any messages. " +
                "Please initialize the Datadog SDK first before" +
                " creating a new Logger instance."
    }
}
