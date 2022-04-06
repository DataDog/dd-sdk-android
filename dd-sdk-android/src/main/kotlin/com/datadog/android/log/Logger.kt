/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import android.util.Log as AndroidLog
import androidx.annotation.FloatRange
import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.sampling.RateBasedSampler
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.logger.CombinedLogHandler
import com.datadog.android.log.internal.logger.DatadogLogHandler
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.logger.LogcatLogHandler
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.log.model.LogEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import org.json.JSONArray
import org.json.JSONObject

/**
 * A class enabling Datadog logging features.
 *
 * It allows you to create a specific context (automatic information, custom attributes, tags) that will be embedded in all
 * logs sent through this logger.
 *
 * You can have multiple loggers configured in your application, each with their own settings.
 */
@Suppress("TooManyFunctions", "MethodOverloading")
class Logger
internal constructor(internal var handler: LogHandler) {

    private val attributes = ConcurrentHashMap<String, Any?>()
    private val tags = CopyOnWriteArraySet<String>()

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
     *
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

    // endregion

    // region Builder

    /**
     * A Builder class for a [Logger].
     */
    class Builder {

        private var serviceName: String = CoreFeature.serviceName
        private var datadogLogsEnabled: Boolean = true
        private var logcatLogsEnabled: Boolean = false
        private var networkInfoEnabled: Boolean = false
        private var bundleWithTraceEnabled: Boolean = true
        private var bundleWithRumEnabled: Boolean = true
        private var loggerName: String = CoreFeature.packageName
        private var sampleRate: Float = 1.0f

        /**
         * Builds a [Logger] based on the current state of this Builder.
         */
        fun build(): Logger {
            val handler = when {
                datadogLogsEnabled && logcatLogsEnabled -> {
                    CombinedLogHandler(
                        buildDatadogHandler(),
                        buildLogcatHandler()
                    )
                }
                datadogLogsEnabled -> buildDatadogHandler()
                logcatLogsEnabled -> buildLogcatHandler()
                else -> NoOpLogHandler()
            }

            return Logger(handler)
        }

        /**
         * Sets the service name that will appear in your logs.
         * @param serviceName the service name (default = application package name)
         */
        fun setServiceName(serviceName: String): Builder {
            this.serviceName = serviceName
            return this
        }

        /**
         * Enables your logs to be sent to the Datadog servers.
         * You can use this feature to disable Datadog logs based on a configuration or an application flavor.
         * @param enabled true by default
         */
        fun setDatadogLogsEnabled(enabled: Boolean): Builder {
            datadogLogsEnabled = enabled
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
        fun setLoggerName(name: String): Builder {
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
         * @param rate the sampling rate, in percent.
         * A value of `0.3` means we'll send 30% of the logs.
         * Default is 1.0 (ie: all logs are sent).
         */
        fun setSampleRate(@FloatRange(from = 0.0, to = 1.0) rate: Float): Builder {
            sampleRate = rate
            return this
        }

        // region Internal

        private fun buildLogcatHandler(): LogHandler {
            return LogcatLogHandler(serviceName, true)
        }

        private fun buildDatadogHandler(): LogHandler {
            val writer = buildLogWriter() ?: return NoOpLogHandler()

            val logGenerator = buildLogGenerator()

            return DatadogLogHandler(
                logGenerator = logGenerator,
                writer = writer,
                bundleWithTraces = bundleWithTraceEnabled,
                bundleWithRum = bundleWithRumEnabled,
                sampler = RateBasedSampler(sampleRate)
            )
        }

        private fun buildLogWriter(): DataWriter<LogEvent>? {
            return if (LogsFeature.isInitialized()) {
                LogsFeature.persistenceStrategy.getWriter()
            } else {
                devLogger.e(
                    SDK_NOT_INITIALIZED_WARNING_MESSAGE + "\n" +
                        Datadog.MESSAGE_SDK_INITIALIZATION_GUIDE
                )
                null
            }
        }

        private fun buildLogGenerator(): LogGenerator {
            val netInfoProvider = if (networkInfoEnabled) {
                CoreFeature.networkInfoProvider
            } else {
                null
            }
            return LogGenerator(
                serviceName,
                loggerName,
                netInfoProvider,
                CoreFeature.userInfoProvider,
                CoreFeature.timeProvider,
                CoreFeature.sdkVersion,
                CoreFeature.envName,
                CoreFeature.packageVersion
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
     * @param value the boolean value of this attribute
     */
    fun addAttribute(key: String, value: Boolean) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the integer value of this attribute
     */
    fun addAttribute(key: String, value: Int) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the long value of this attribute
     */
    fun addAttribute(key: String, value: Long) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the float value of this attribute
     */
    fun addAttribute(key: String, value: Float) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the double value of this attribute
     */
    fun addAttribute(key: String, value: Double) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the (nullable) String value of this attribute
     */
    fun addAttribute(key: String, value: String?) {
        safelyAddAttribute(key, value)
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the (nullable) [Date] value of this attribute
     */
    fun addAttribute(key: String, value: Date?) {
        safelyAddAttribute(key, value)
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the (nullable) [JsonObject] value of this attribute
     */
    fun addAttribute(key: String, value: JsonObject?) {
        safelyAddAttribute(key, value)
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the (nullable) [JsonArray] value of this attribute
     */
    fun addAttribute(key: String, value: JsonArray?) {
        safelyAddAttribute(key, value)
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the (nullable) [JSONObject] value of this attribute
     */
    fun addAttribute(key: String, value: JSONObject?) {
        safelyAddAttribute(key, value)
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the (nullable) [JSONArray] value of this attribute
     */
    fun addAttribute(key: String, value: JSONArray?) {
        safelyAddAttribute(key, value)
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
        handler.handleLog(level, message, throwable, combinedAttributes, tags, timestamp)
    }

    private fun addTagInternal(tag: String) {
        tags.add(tag)
    }

    private fun removeTagInternal(tag: String) {
        tags.remove(tag)
    }

    private fun safelyAddAttribute(key: String, value: Any?) {
        val attributeValue = value ?: NULL_MAP_VALUE
        attributes[key] = attributeValue
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
        internal const val SDK_NOT_INITIALIZED_WARNING_MESSAGE =
            "You're trying to create a Logger instance, but the SDK was not yet initialized. " +
                "This Logger will not be able to send any messages. " +
                "Please initialize the Datadog SDK first before" +
                " creating a new Logger instance."
    }
}
