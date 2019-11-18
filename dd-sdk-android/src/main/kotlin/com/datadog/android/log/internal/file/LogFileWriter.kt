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
import com.datadog.android.log.internal.LogWriter
import com.google.gson.JsonObject
import java.io.File
import java.io.FileFilter
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
        jsonLog.addProperty(TAG_MESSAGE, log.message)
        jsonLog.addProperty(TAG_SERVICE_NAME, log.serviceName)
        jsonLog.addProperty(TAG_STATUS, logLevelStatusName(log.level))

        // User Agent
        log.userAgent?.let { jsonLog.addProperty(TAG_USER_AGENT_SDK, it) }

        // Timestamp
        log.timestamp?.let {
            val formattedDate = simpleDateFormat.format(Date(log.timestamp))
            jsonLog.addProperty(TAG_DATE, formattedDate)
        }

        // TODO Network Infos

        // TODO Custom Fields

        // TODO Tags

        return jsonLog.toString()
    }

    private fun obfuscate(log: String): ByteArray {
        val input = log.toByteArray(Charsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || Build.MODEL == null) {
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
            AndroidLog.ASSERT -> "ERROR"
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

        internal const val TAG_USER_AGENT_SDK = "http.useragent_sdk"
        internal const val TAG_NETWORK_INFO = "networkinfo"
        internal const val TAG_MESSAGE = "message"
        internal const val TAG_STATUS = "status"
        internal const val TAG_SERVICE_NAME = "service"
        internal const val TAG_DATE = "date"
        internal const val TAG_DATADOG_TAGS = "ddtags"
    }
}
