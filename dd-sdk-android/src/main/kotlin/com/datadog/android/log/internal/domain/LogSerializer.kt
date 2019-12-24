/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.log.internal.constraints.DatadogLogConstraints
import com.datadog.android.log.internal.constraints.LogConstraints
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
        jsonLog.addProperty(TAG_MESSAGE, log.message)
        jsonLog.addProperty(TAG_SERVICE_NAME, log.serviceName)
        jsonLog.addProperty(TAG_STATUS, resolveLogLevelStatus(log.level))
        jsonLog.addProperty(TAG_LOGGER_NAME, log.loggerName)
        jsonLog.addProperty(TAG_THREAD_NAME, log.threadName)

        // Timestamp
        val formattedDate = simpleDateFormat.format(Date(log.timestamp))
        jsonLog.addProperty(TAG_DATE, formattedDate)

        // Network Infos
        addLogNetworkInfo(log, jsonLog)

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
            jsonLog.addProperty(TAG_NETWORK_CONNECTIVITY, info.connectivity.serialized)
            if (!info.carrierName.isNullOrBlank()) {
                jsonLog.addProperty(TAG_NETWORK_CARRIER_NAME, info.carrierName)
            }
            if (info.carrierId >= 0) {
                jsonLog.addProperty(TAG_NETWORK_CARRIER_ID, info.carrierId)
            }
        }
    }

    private fun addLogThrowable(
        log: Log,
        jsonLog: JsonObject
    ) {
        log.throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            jsonLog.addProperty(TAG_ERROR_KIND, it.javaClass.simpleName)
            jsonLog.addProperty(TAG_ERROR_MESSAGE, it.message)
            jsonLog.addProperty(TAG_ERROR_STACK, sw.toString())
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
                    null -> JsonNull.INSTANCE
                    is Boolean -> JsonPrimitive(value)
                    is Int -> JsonPrimitive(value)
                    is Long -> JsonPrimitive(value)
                    is Float -> JsonPrimitive(value)
                    is Double -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    is Date -> JsonPrimitive(value.time)
                    else -> JsonPrimitive(value.toString())
                }
                jsonLog.add(it.key, jsonValue)
            }
    }

    companion object {
        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSX"

        // MAIN TAGS
        internal const val TAG_HOST = "host"
        internal const val TAG_MESSAGE = "message"
        internal const val TAG_STATUS = "status"
        internal const val TAG_SERVICE_NAME = "service"
        internal const val TAG_SOURCE = "source"
        internal const val TAG_DATE = "date"

        // COMMON TAGS
        internal const val TAG_DATADOG_TAGS = "ddtags"

        // ERROR TAGS
        internal const val TAG_ERROR_KIND = "error.kind"
        internal const val TAG_ERROR_MESSAGE = "error.message"
        internal const val TAG_ERROR_STACK = "error.stack"

        // THREAD RELATED TAGS
        internal const val TAG_LOGGER_NAME = "logger.name"
        internal const val TAG_THREAD_NAME = "logger.thread_name"

        // ANDROID SPECIFIC TAGS
        internal const val TAG_NETWORK_CONNECTIVITY = "network.client.connectivity"
        internal const val TAG_NETWORK_CARRIER_NAME = "network.client.sim_carrier.name"
        internal const val TAG_NETWORK_CARRIER_ID = "network.client.sim_carrier.id"

        internal val reservedAttributes = arrayOf(
            TAG_HOST,
            TAG_MESSAGE,
            TAG_STATUS,
            TAG_SERVICE_NAME,
            TAG_SOURCE,
            TAG_ERROR_KIND,
            TAG_ERROR_MESSAGE,
            TAG_ERROR_STACK,
            TAG_DATADOG_TAGS
        )

        internal fun resolveLogLevelStatus(level: Int): String {
            return when (level) {
                android.util.Log.ASSERT -> "CRITICAL"
                android.util.Log.ERROR -> "ERROR"
                android.util.Log.WARN -> "WARN"
                android.util.Log.INFO -> "INFO"
                android.util.Log.DEBUG -> "DEBUG"
                android.util.Log.VERBOSE -> "TRACE"
                else -> "DEBUG"
            }
        }
    }
}
