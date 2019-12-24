/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.annotation.TargetApi
import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.Writer
import android.util.Base64 as AndroidBase64
import android.util.Log as AndroidLog
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.constraints.LogConstraints
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.file.LogFileStrategy
import com.datadog.android.log.internal.thread.LazyHandlerThread
import com.datadog.android.log.internal.utils.sdkLogger
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Base64 as JavaBase64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class FileWriter(
    private val fileOrchestrator: Orchestrator,
    private val logConstraints: LogConstraints,
    rootDirectory: File
) : LazyHandlerThread(THREAD_NAME),
    Writer {

    private val simpleDateFormat = SimpleDateFormat(ISO_8601, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val writeable: Boolean = if (!rootDirectory.exists()) {
        rootDirectory.mkdirs()
    } else {
        rootDirectory.isDirectory
    }

    init {
        if (!writeable) {
            sdkLogger.e(
                "$TAG: Can't write logs on disk: directory ${rootDirectory.path} is invalid."
            )
        } else {
            start()
        }
    }

    // region LogWriter

    override fun writeLog(log: Log) {
        if (!writeable) return

        post(Runnable {
            val strLog = serializeLog(log)

            if (strLog.length >= MAX_LOG_SIZE) {
                // TODO RUMM-49 warn user that the log is too big !
            } else {
                obfuscateAndWriteLog(strLog)
            }
        })
    }

    // endregion

    // region Internal

    private fun serializeLog(log: Log): String {
        val jsonLog = JsonObject()

        // Mandatory info
        jsonLog.addProperty(LogStrategy.TAG_MESSAGE, log.message)
        jsonLog.addProperty(LogStrategy.TAG_SERVICE_NAME, log.serviceName)
        jsonLog.addProperty(LogStrategy.TAG_STATUS, logLevelStatusName(log.level))
        jsonLog.addProperty(LogStrategy.TAG_LOGGER_NAME, log.loggerName)
        jsonLog.addProperty(LogStrategy.TAG_THREAD_NAME, log.threadName)

        // Timestamp
        val formattedDate = simpleDateFormat.format(Date(log.timestamp))
        jsonLog.addProperty(LogStrategy.TAG_DATE, formattedDate)

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
            jsonLog.addProperty(LogStrategy.TAG_NETWORK_CONNECTIVITY, info.connectivity.serialized)
            if (!info.carrierName.isNullOrBlank()) {
                jsonLog.addProperty(LogStrategy.TAG_NETWORK_CARRIER_NAME, info.carrierName)
            }
            if (info.carrierId >= 0) {
                jsonLog.addProperty(LogStrategy.TAG_NETWORK_CARRIER_ID, info.carrierId)
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
            jsonLog.addProperty(LogStrategy.TAG_ERROR_KIND, it.javaClass.simpleName)
            jsonLog.addProperty(LogStrategy.TAG_ERROR_MESSAGE, it.message)
            jsonLog.addProperty(LogStrategy.TAG_ERROR_STACK, sw.toString())
        }
    }

    private fun addLogTags(
        log: Log,
        jsonLog: JsonObject
    ) {
        val tags = logConstraints.validateTags(log.tags)
            .joinToString(",")
        jsonLog.addProperty(LogStrategy.TAG_DATADOG_TAGS, tags)
    }

    private fun addLogAttributes(
        log: Log,
        jsonLog: JsonObject
    ) {
        logConstraints.validateAttributes(log.attributes)
            .filter { it.key.isNotBlank() && it.key !in LogStrategy.reservedAttributes }
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

    private fun obfuscateAndWriteLog(strLog: String) {
        val obfLog = obfuscate(strLog)

        synchronized(this) {
            writeLogSafely(obfLog)
        }
    }

    private fun writeLogSafely(obfLog: ByteArray) {
        var file: File? = null
        try {
            file = fileOrchestrator.getWritableFile(obfLog.size)
            file.appendBytes(obfLog)
            file.appendBytes(logSeparator)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("$TAG: Couldn't create an output stream to file ${file?.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("$TAG: Couldn't write log to file ${file?.path}", e)
        } catch (e: SecurityException) {
            sdkLogger.e("$TAG: Couldn't access file ${file?.path}", e)
        }
    }

    private fun obfuscate(log: String): ByteArray {
        val input = log.toByteArray(Charsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            obfuscateApi26(input)
        } else {
            AndroidBase64.encode(input, AndroidBase64.NO_WRAP)
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun obfuscateApi26(input: ByteArray): ByteArray {
        val encoder = JavaBase64.getEncoder()
        return encoder.encode(input)
    }

    private fun logLevelStatusName(level: Int): String {
        return when (level) {
            AndroidLog.ASSERT -> "CRITICAL"
            AndroidLog.ERROR -> "ERROR"
            AndroidLog.WARN -> "WARN"
            AndroidLog.INFO -> "INFO"
            AndroidLog.DEBUG -> "DEBUG"
            AndroidLog.VERBOSE -> "TRACE"
            else -> "DEBUG"
        }
    }

    // endregion

    companion object {
        private val logSeparator = ByteArray(1) { LogFileStrategy.SEPARATOR_BYTE }

        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSX"
        private const val THREAD_NAME = "ddog_w"

        private const val MAX_LOG_SIZE = 256 * 1024 // 256 Kb

        private const val TAG = "LogFileWriter"
    }
}
