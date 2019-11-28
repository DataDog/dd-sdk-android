/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.os.Build
import android.util.Log as AndroidLog
import com.datadog.android.Datadog
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogWriter
import com.datadog.android.log.internal.file.DummyLogWriter
import com.datadog.android.log.internal.net.NetworkInfoProvider
import java.util.Date
import java.util.Locale

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
private constructor(
    val serviceName: String,
    val timestampsEnabled: Boolean,
    val datadogLogsEnabled: Boolean,
    val logcatLogsEnabled: Boolean,
    val userAgentEnabled: Boolean,
    val userAgent: String,
    private val logWriter: LogWriter,
    internal val networkInfoProvider: NetworkInfoProvider?
) {

    private val attributes = mutableMapOf<String, Any?>()
    private val tags = mutableSetOf<String>()

    // region Log

    /**
     * Sends a VERBOSE log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun v(message: String, throwable: Throwable? = null) {
        internalLog(AndroidLog.VERBOSE, message, throwable)
    }

    /**
     * Sends a Debug log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun d(message: String, throwable: Throwable? = null) {
        internalLog(AndroidLog.DEBUG, message, throwable)
    }

    /**
     * Sends an Info log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun i(message: String, throwable: Throwable? = null) {
        internalLog(AndroidLog.INFO, message, throwable)
    }

    /**
     * Sends a Warning log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun w(message: String, throwable: Throwable? = null) {
        internalLog(AndroidLog.WARN, message, throwable)
    }

    /**
     * Sends an Error log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun e(message: String, throwable: Throwable? = null) {
        internalLog(AndroidLog.ERROR, message, throwable)
    }

    /**
     * Sends an Assert log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun wtf(message: String, throwable: Throwable? = null) {
        internalLog(AndroidLog.ASSERT, message, throwable)
    }

    // endregion

    // region Builder

    /**
     * A Builder class for a [Logger].
     */
    class Builder {

        private var serviceName: String = DEFAULT_SERVICE_NAME
        private var timestampsEnabled: Boolean = true
        private var userAgentEnabled: Boolean = true
        private var datadogLogsEnabled: Boolean = true
        private var logcatLogsEnabled: Boolean = false
        private var networkInfoEnabled: Boolean = false

        private var logStrategy: LogStrategy? = null
        private var userAgent: String? = null
        private var networkInfoProvider: NetworkInfoProvider? = null

        /**
         * Builds a [Logger] based on the current state of this Builder.
         */
        fun build(): Logger {

            // TODO RUMM-45 register broadcast receiver

            return Logger(
                datadogLogsEnabled = datadogLogsEnabled,
                logWriter = logWriter,
                serviceName = serviceName,
                timestampsEnabled = timestampsEnabled,
                userAgentEnabled = userAgentEnabled,
                // TODO RUMM-34 allow overriding the user agent ?
                userAgent = userAgent ?: System.getProperty("http.agent").orEmpty(),
                logcatLogsEnabled = logcatLogsEnabled,
                networkInfoProvider = if (networkInfoEnabled && datadogLogsEnabled) {
                    networkInfoProvider ?: Datadog.getNetworkInfoProvider()
                } else null
            )
        }

        private val logWriter: LogWriter
            get() {
                return if (datadogLogsEnabled) {
                    (logStrategy ?: Datadog.getLogStrategy()).getLogWriter()
                } else {
                    DummyLogWriter()
                }
            }

        /**
         * Sets the service name that will appear in your logs.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): Builder {
            this.serviceName = serviceName
            return this
        }

        /**
         * Enables timestamp to be automatically added in your logs.
         * @param enabled true by default
         */
        fun setTimestampsEnabled(enabled: Boolean): Builder {
            timestampsEnabled = enabled
            return this
        }

        /**
         * Enables the system User Agent to be automatically added in your logs.
         * @param enabled true by default
         */
        fun setUserAgentEnabled(enabled: Boolean): Builder {
            userAgentEnabled = enabled
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

        private fun overrideLogStrategy(strategy: LogStrategy): Builder {
            logStrategy = strategy
            return this
        }

        private fun overrideUserAgent(userAgent: String): Builder {
            this.userAgent = userAgent
            return this
        }

        private fun overrideNetworkInfoProvider(provider: NetworkInfoProvider): Builder {
            networkInfoProvider = provider
            return this
        }
    }

    // endregion

    // region Context Information (attributes, tags)

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the boolean value of this attribute
     */
    fun addAttribute(key: String, value: Boolean) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the integer value of this attribute
     */
    fun addAttribute(key: String, value: Int) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the long value of this attribute
     */
    fun addAttribute(key: String, value: Long) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the float value of this attribute
     */
    fun addAttribute(key: String, value: Float) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the double value of this attribute
     */
    fun addAttribute(key: String, value: Double) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the (nullable) String value of this attribute
     */
    fun addAttribute(key: String, value: String?) {
        attributes[key] = value
    }

    /**
     * Add a custom attribute to all future logs sent by this logger.
     * @param key the key for this attribute
     * @param value the (nullable) Date value of this attribute
     */
    fun addAttribute(key: String, value: Date?) {
        attributes[key] = value
    }

    /**
     * Remove a custom attribute from all future logs sent by this logger.
     * Previous log won't lose the attribute value associated with this key if they were created
     * prior to this call.
     * @param key the key of the attribute to remove
     */
    fun removeAttribute(key: String) {
        attributes.remove(key)
    }

    /**
     * Add a tag to all future logs sent by this logger.
     * The tag will take the form "key:value"
     *
     * Tags must start with a letter and after that may contain the following characters:
     * Alphanumerics, Underscores, Minuses, Colons, Periods,Slashes. Other special characters
     * are converted to underscores.
     * Tags must be lowercase, and can be at most 200 characters. If the tag you provide is
     * longer, only the first 200 characters will be used.
     *
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
     * Alphanumerics, Underscores, Minuses, Colons, Periods,Slashes. Other special characters
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
     * Previous log won't lose the this tag if they were created prior to this call.
     * @param tag the tag to remove
     */
    fun removeTag(tag: String) {
        removeTagInternal(tag)
    }

    // endregion

    // region Internal/Log

    private fun internalLog(
        level: Int,
        message: String,
        throwable: Throwable?
    ) {
        if (logcatLogsEnabled) {
            if (Build.MODEL == null) {
                println("${levelPrefixes[level]}/$serviceName: $message")
            } else {
                AndroidLog.println(level, serviceName, message)
            }
        }

        if (datadogLogsEnabled) {
            val log = createLog(level, message, throwable)
            logWriter.writeLog(log)
        }
    }

    private fun createLog(level: Int, message: String, throwable: Throwable?): Log {
        // TODO RUMM-58 timestamp based on phone local time = error prone

        return Log(
            serviceName = serviceName,
            level = level,
            message = message,
            timestamp = if (timestampsEnabled) System.currentTimeMillis() else null,
            userAgent = if (userAgentEnabled) userAgent else null,
            throwable = throwable,
            attributes = attributes.toMap(),
            tags = tags.toList(),
            networkInfo = networkInfoProvider?.getLatestNetworkInfos()
        )
    }

    // endregion

    // region Internal/Tag

    private fun addTagInternal(tag: String) {
        val convertedTag = convertTag(tag)
        if (convertedTag != null) {
            // TODO RUMM-49 warn if tag value was modified automatically
            if (isKeyValid(convertedTag)) {
                tags.add(convertedTag)
            } else {
                // TODO RUMM-49 warn that tag key is reserved
                // Do nothing
            }
        } else {
            // TODO RUMM-49 print warning that the tag is illegal and cannot be converted
            // Do nothing
        }
    }

    private fun removeTagInternal(tag: String) {
        val convertedTag = convertTag(tag)
        if (convertedTag != null) {
            tags.remove(convertedTag)
        } else {
            // TODO RUMM-49 print warning that the tag is illegal and cannot be converted
            // Do nothing
        }
    }

    private fun convertTag(tag: String): String? {
        val lowerCaseTag = tag.toLowerCase(Locale.US)
        return if (lowerCaseTag[0] !in 'a'..'z') {
            null
        } else {
            val valid = lowerCaseTag.replace(Regex("[^a-z0-9_:./-]"), "_")
            if (valid.length <= MAX_TAG_LENGTH) {
                valid
            } else {
                valid.substring(0, MAX_TAG_LENGTH)
            }
        }
    }

    private fun isKeyValid(tag: String): Boolean {
        val firstColon = tag.indexOf(':')
        return if (firstColon > 0) {
            val key = tag.substring(0, firstColon)
            key !in reservedTagKeys
        } else {
            true
        }
    }

    // endregion

    companion object {
        const val DEFAULT_SERVICE_NAME = "android"

        const val MAX_TAG_LENGTH = 200

        private val reservedTagKeys = setOf(
            "host", "device", "source", "service"
        )

        private val levelPrefixes = mapOf(
            AndroidLog.VERBOSE to "V",
            AndroidLog.DEBUG to "D",
            AndroidLog.INFO to "I",
            AndroidLog.WARN to "W",
            AndroidLog.ERROR to "E",
            AndroidLog.ASSERT to "A"
        )
    }
}
