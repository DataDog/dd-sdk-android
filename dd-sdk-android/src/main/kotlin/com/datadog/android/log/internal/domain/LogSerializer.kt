/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.util.Log as AndroidLog
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.constraints.DatadogLogConstraints
import com.datadog.android.log.internal.constraints.LogConstraints
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Logging feature implementation of the [Serializer] interface.
 */
internal class LogSerializer(private val logConstraints: LogConstraints = DatadogLogConstraints()) :
        Serializer<Log> {

    private val simpleDateFormat = SimpleDateFormat(ISO_8601, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun serialize(model: Log): String {
        return serializeLog(model)
    }

    private fun serializeLog(log: Log): String {
        val jsonLog = JsonObject()

        // Mandatory info
        jsonLog.addProperty(LogAttributes.MESSAGE, log.message)
        jsonLog.addProperty(LogAttributes.SERVICE_NAME, log.serviceName)
        jsonLog.addProperty(LogAttributes.STATUS, resolveLogLevelStatus(log.level))
        jsonLog.addProperty(LogAttributes.LOGGER_NAME, log.loggerName)
        jsonLog.addProperty(LogAttributes.LOGGER_THREAD_NAME, log.threadName)
        jsonLog.addProperty(LogAttributes.LOGGER_VERSION, BuildConfig.VERSION_NAME)
        jsonLog.addProperty(LogAttributes.APPLICATION_VERSION, CoreFeature.packageVersion)
        jsonLog.addProperty(LogAttributes.APPLICATION_PACKAGE, CoreFeature.packageName)

        // Timestamp
        val formattedDate = synchronized(simpleDateFormat) {
            simpleDateFormat.format(Date(log.timestamp))
        }
        jsonLog.addProperty(LogAttributes.DATE, formattedDate)

        // Network Info
        addLogNetworkInfo(log, jsonLog)

        // User Info
        addLogUserInfo(log, jsonLog)

        // Custom Attributes
        addLogAttributes(log, jsonLog)

        // Tags
        addLogTags(log, jsonLog)

        // Throwable
        addLogThrowable(log, jsonLog)

        return jsonLog.toString()
    }

    private fun addLogNetworkInfo(
        log: Log,
        jsonLog: JsonObject
    ) {
        val info = log.networkInfo
        if (info != null) {
            jsonLog.addProperty(LogAttributes.NETWORK_CONNECTIVITY, info.connectivity.serialized)
            if (!info.carrierName.isNullOrBlank()) {
                jsonLog.addProperty(LogAttributes.NETWORK_CARRIER_NAME, info.carrierName)
            }
            if (info.carrierId >= 0) {
                jsonLog.addProperty(LogAttributes.NETWORK_CARRIER_ID, info.carrierId)
            }
            if (info.upKbps >= 0) {
                jsonLog.addProperty(LogAttributes.NETWORK_UP_KBPS, info.upKbps)
            }
            if (info.downKbps >= 0) {
                jsonLog.addProperty(LogAttributes.NETWORK_DOWN_KBPS, info.downKbps)
            }
            if (info.strength > Int.MIN_VALUE) {
                jsonLog.addProperty(LogAttributes.NETWORK_SIGNAL_STRENGTH, info.strength)
            }
        }
    }

    private fun addLogUserInfo(log: Log, jsonLog: JsonObject) {
        val userInfo = log.userInfo
        if (!userInfo.id.isNullOrEmpty()) {
            jsonLog.addProperty(LogAttributes.USR_ID, userInfo.id)
        }
        if (!userInfo.name.isNullOrEmpty()) {
            jsonLog.addProperty(LogAttributes.USR_NAME, userInfo.name)
        }
        if (!userInfo.email.isNullOrEmpty()) {
            jsonLog.addProperty(LogAttributes.USR_EMAIL, userInfo.email)
        }
    }

    private fun addLogThrowable(
        log: Log,
        jsonLog: JsonObject
    ) {
        log.throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            jsonLog.addProperty(LogAttributes.ERROR_KIND, it.javaClass.simpleName)
            jsonLog.addProperty(LogAttributes.ERROR_MESSAGE, it.message)
            jsonLog.addProperty(LogAttributes.ERROR_STACK, sw.toString())
        }
    }

    private fun addLogTags(
        log: Log,
        jsonLog: JsonObject
    ) {
        val tags = logConstraints.validateTags(log.tags)
                .joinToString(",")
        jsonLog.addProperty(TAG_DATADOG_TAGS, tags)
    }

    private fun addLogAttributes(
        log: Log,
        jsonLog: JsonObject
    ) {
        logConstraints.validateAttributes(log.attributes)
                .filter { it.key.isNotBlank() && it.key !in reservedAttributes }
                .forEach {
                    val value = it.value
                    val jsonValue = when (value) {
                        NULL_MAP_VALUE -> JsonNull.INSTANCE
                        is Boolean -> JsonPrimitive(value)
                        is Int -> JsonPrimitive(value)
                        is Long -> JsonPrimitive(value)
                        is Float -> JsonPrimitive(value)
                        is Double -> JsonPrimitive(value)
                        is String -> JsonPrimitive(value)
                        is Date -> JsonPrimitive(value.time)
                        is JsonObject -> value
                        is JsonArray -> value
                        else -> JsonPrimitive(value.toString())
                    }
                    jsonLog.add(it.key, jsonValue)
                }
    }

    companion object {
        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        internal const val TAG_DATADOG_TAGS = "ddtags"

        internal val reservedAttributes = arrayOf(
                LogAttributes.HOST,
                LogAttributes.MESSAGE,
                LogAttributes.STATUS,
                LogAttributes.SERVICE_NAME,
                LogAttributes.SOURCE,
                LogAttributes.ERROR_KIND,
                LogAttributes.ERROR_MESSAGE,
                LogAttributes.ERROR_STACK,
                TAG_DATADOG_TAGS
        )

        internal fun resolveLogLevelStatus(level: Int): String {
            return when (level) {
                AndroidLog.ASSERT -> "CRITICAL"
                AndroidLog.ERROR -> "ERROR"
                AndroidLog.WARN -> "WARN"
                AndroidLog.INFO -> "INFO"
                AndroidLog.DEBUG -> "DEBUG"
                AndroidLog.VERBOSE -> "TRACE"
                Log.CRASH -> "EMERGENCY"
                else -> "DEBUG"
            }
        }
    }
}
