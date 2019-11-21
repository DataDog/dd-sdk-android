/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.annotation.TargetApi
import android.os.Build
import android.util.Base64 as AndroidBase64
import android.util.Log as AndroidLog
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogWriter
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.File
import java.io.FileFilter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Base64 as JavaBase64
import java.util.Date
import java.util.Locale

internal class LogFileWriter(
    private val rootDirectory: File,
    private val recentDelayMs: Long
) : LogWriter {

    private val writeable: Boolean
    private val simpleDateFormat = SimpleDateFormat(ISO_8601, Locale.US)
    private val fileFilter: FileFilter = LogFileFilter()

    init {
        writeable = if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        } else {
            rootDirectory.isDirectory
        }
        if (!writeable) {
            AndroidLog.e(
                "datadog",
                "Can't write logs on disk: directory ${rootDirectory.path} is invalid."
            )
        }
    }

    // region LoggerWriter

    override fun writeLog(log: Log) {
        if (!writeable) return

        val strLog = serializeLog(log)
        val obfLog = obfuscate(strLog)

        val file = getWritableFile(obfLog.size)
        file.appendBytes(obfLog)
        file.appendBytes(logSeparator)
    }

    // endregion

    // region Internal

    private fun getWritableFile(logSize: Int): File {
        val maxLogLength = MAX_BATCH_SIZE - logSize
        val now = System.currentTimeMillis()

        val files = rootDirectory.listFiles(fileFilter).sorted()
        val lastFile = files.lastOrNull()

        return if (lastFile != null) {
            val fileHasRoomForMore = lastFile.length() < maxLogLength
            val fileIsRecentEnough = LogFileStrategy.isFileRecent(lastFile, recentDelayMs)

            if (fileHasRoomForMore && fileIsRecentEnough) {
                lastFile
            } else {
                File(rootDirectory, now.toString())
            }
        } else {
            File(rootDirectory, now.toString())
        }
    }

    private fun serializeLog(log: Log): String {
        val jsonLog = JsonObject()

        // Mandatory info
        jsonLog.addProperty(LogStrategy.TAG_MESSAGE, log.message)
        jsonLog.addProperty(LogStrategy.TAG_SERVICE_NAME, log.serviceName)
        jsonLog.addProperty(LogStrategy.TAG_STATUS, logLevelStatusName(log.level))

        // User Agent
        log.userAgent?.let { jsonLog.addProperty(LogStrategy.TAG_USER_AGENT_SDK, it) }

        // Timestamp
        log.timestamp?.let {
            val formattedDate = simpleDateFormat.format(Date(log.timestamp))
            jsonLog.addProperty(LogStrategy.TAG_DATE, formattedDate)
        }

        // Network Infos
        val info = log.networkInfo
        if (info != null) {
            val network = JsonObject()
            network.addProperty(LogStrategy.TAG_NETWORK_CONNECTIVITY, info.connectivity.serialized)
            if (!info.carrierName.isNullOrBlank()) {
                network.addProperty(LogStrategy.TAG_NETWORK_CARRIER_NAME, info.carrierName)
            }
            if (info.carrierId >= 0) {
                network.addProperty(LogStrategy.TAG_NETWORK_CARRIER_ID, info.carrierId)
            }
            jsonLog.add(LogStrategy.TAG_NETWORK_INFO, network)
        }

        // Custom Attributes
        log.attributes
            .filter { it.key.isNotBlank() && it.key !in LogStrategy.reservedAttributes }
            .forEach {
                val value = it.value
                val jsonValue = when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Int -> JsonPrimitive(value)
                    is Long -> JsonPrimitive(value)
                    is Float -> JsonPrimitive(value)
                    is Double -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    is Date -> JsonPrimitive(value.time)
                    else -> JsonNull.INSTANCE
                }
                jsonLog.add(it.key, jsonValue)
            }

        // Tags
        val tags = log.tags
            .filter { it.key.isNotBlank() }
            .map { "${it.key}:${it.value}" }
            .joinToString(",")
        jsonLog.addProperty(LogStrategy.TAG_DATADOG_TAGS, tags)

        // Throwable
        log.throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            jsonLog.addProperty(LogStrategy.TAG_ERROR_KIND, it.javaClass.simpleName)
            jsonLog.addProperty(LogStrategy.TAG_ERROR_MESSAGE, it.message)
            jsonLog.addProperty(LogStrategy.TAG_ERROR_STACK, sw.toString())
        }

        return jsonLog.toString()
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

        private const val MAX_BATCH_SIZE: Long = 16 * 1024
        private val logSeparator = ByteArray(1) { LogFileStrategy.SEPARATOR_BYTE }

        private const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    }
}
