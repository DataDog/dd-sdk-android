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
import java.util.Date

/**
 * A class enabling Datadog logging features.
 *
 * It allows you to create a specific context (automatic information, custom fields, tags) that will be embedded in all
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
    val networkInfoEnabled: Boolean,
    val userAgentEnabled: Boolean,
    val userAgent: String,
    strategy: LogStrategy
) {

    private val logWriter = strategy.getLogWriter()
    private val fields = mutableMapOf<String, Any?>()

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

        /**
         * Builds a [Logger] based on the current state of this Builder.
         */
        fun build(): Logger {

            // TODO register broadcast receiver

            return Logger(
                strategy = logStrategy ?: Datadog.getLogStrategy(),
                serviceName = serviceName,
                timestampsEnabled = timestampsEnabled,
                userAgentEnabled = userAgentEnabled,
                // TODO xgouchet 2019/11/5 allow overriding the user agent ?
                userAgent = userAgent ?: System.getProperty("http.agent").orEmpty(),
                datadogLogsEnabled = datadogLogsEnabled,
                logcatLogsEnabled = logcatLogsEnabled,
                networkInfoEnabled = networkInfoEnabled
            )
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

        internal fun overrideLogStrategy(strategy: LogStrategy): Builder {
            logStrategy = strategy
            return this
        }

        internal fun overrideUserAgent(userAgent: String): Builder {
            this.userAgent = userAgent
            return this
        }
    }

    // endregion

    // region Context Information (fields, tags)

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the boolean value of this field
     */
    fun addField(key: String, value: Boolean) {
        fields[key] = value
    }

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the integer value of this field
     */
    fun addField(key: String, value: Int) {
        fields[key] = value
    }

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the long value of this field
     */
    fun addField(key: String, value: Long) {
        fields[key] = value
    }

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the float value of this field
     */
    fun addField(key: String, value: Float) {
        fields[key] = value
    }

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the double value of this field
     */
    fun addField(key: String, value: Double) {
        fields[key] = value
    }

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the (nullable) String value of this field
     */
    fun addField(key: String, value: String?) {
        fields[key] = value
    }

    /**
     * Add a custom field to all future logs sent by this logger.
     * @param key the key for this field
     * @param value the (nullable) Date value of this field
     */
    fun addField(key: String, value: Date?) {
        fields[key] = value
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
        // TODO timestamp based on phone local time = error prone

        // TODO build log object with relevant infos : fields, tags, networkInfo

        // TODO include information about the throwable

        return Log(
            serviceName = serviceName,
            level = level,
            message = message,
            timestamp = if (timestampsEnabled) System.currentTimeMillis() else null,
            userAgent = if (userAgentEnabled) userAgent else null,
            throwable = throwable,
            fields = fields
        )
    }

    // endregion

    companion object {
        const val DEFAULT_SERVICE_NAME = "android"

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
