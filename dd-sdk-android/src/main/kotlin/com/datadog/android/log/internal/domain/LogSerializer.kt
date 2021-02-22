/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.util.Log as AndroidLog
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.toJsonElement
import com.datadog.android.log.LogAttributes
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Logging feature implementation of the [Serializer] interface.
 */
internal class LogSerializer(
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) :
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
            jsonLog.addProperty(
                LogAttributes.NETWORK_CONNECTIVITY,
                info.connectivity.toJson().asString
            )
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
        // add extra info
        dataConstraints.validateAttributes(
            userInfo.extraInfo,
            keyPrefix = LogAttributes.USR_ATTRIBUTES_GROUP,
            attributesGroupName = USER_EXTRA_GROUP_VERBOSE_NAME
        ).forEach {
            val key = "${LogAttributes.USR_ATTRIBUTES_GROUP}.${it.key}"
            jsonLog.add(key, it.value.toJsonElement())
        }
    }

    private fun addLogThrowable(
        log: Log,
        jsonLog: JsonObject
    ) {
        log.throwable?.let {
            jsonLog.addProperty(
                LogAttributes.ERROR_KIND,
                it.javaClass.canonicalName ?: it.javaClass.simpleName
            )
            jsonLog.addProperty(LogAttributes.ERROR_MESSAGE, it.message)
            jsonLog.addProperty(LogAttributes.ERROR_STACK, it.loggableStackTrace())
        }
    }

    private fun addLogTags(
        log: Log,
        jsonLog: JsonObject
    ) {
        val tags = dataConstraints.validateTags(log.tags)
            .joinToString(",")
        jsonLog.addProperty(TAG_DATADOG_TAGS, tags)
    }

    private fun addLogAttributes(
        log: Log,
        jsonLog: JsonObject
    ) {
        dataConstraints.validateAttributes(log.attributes)
            .filter { it.key.isNotBlank() && it.key !in reservedAttributes }
            .forEach {
                val jsonValue = it.value.toJsonElement()
                jsonLog.add(it.key, jsonValue)
            }
    }

    companion object {
        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

        internal const val TAG_DATADOG_TAGS = "ddtags"
        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"

        internal val reservedAttributes = arrayOf(
            TAG_DATADOG_TAGS
        )

        internal fun resolveLogLevelStatus(level: Int): String {
            return when (level) {
                AndroidLog.ASSERT -> "critical"
                AndroidLog.ERROR -> "error"
                AndroidLog.WARN -> "warn"
                AndroidLog.INFO -> "info"
                AndroidLog.DEBUG -> "debug"
                AndroidLog.VERBOSE -> "trace"
                // If you change these you will have to propagate the changes
                // also into the datadog-native-lib.cpp file inside the dd-sdk-android-ndk module.
                Log.CRASH -> "emergency"
                else -> "debug"
            }
        }
    }
}
