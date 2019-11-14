/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.annotation.TargetApi
import android.os.Build
import android.util.Base64 as AndroidBase64
import com.datadog.android.log.internal.LogReader
import com.datadog.android.log.internal.utils.split
import java.io.File
import java.io.FileFilter
import java.util.Base64 as JavaBase64

internal class LogFileReader(private val rootDirectory: File) : LogReader {

    private val fileFilter: FileFilter = LogFileFilter()

    // region LogReader

    override fun readNextLog(): String? {
        return readNextBatch().firstOrNull()
    }

    override fun readNextBatch(): List<String> {
        val files = rootDirectory.listFiles(fileFilter).sorted()
        val nextLogFile = files.firstOrNull()
        return if (nextLogFile == null) {
            emptyList()
        } else {
            val inputBytes = nextLogFile.readBytes()
            val logs = inputBytes.split(LogFileStrategy.SEPARATOR_BYTE)

            logs.map { deobfuscate(it) }
        }
    }

    // endregion

    // region Internal

    private fun deobfuscate(input: ByteArray): String {
        val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || Build.MODEL == null) {
            deobfuscateApi26(input)
        } else {
            AndroidBase64.decode(input, AndroidBase64.DEFAULT)
        }

        return String(output, Charsets.UTF_8)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun deobfuscateApi26(input: ByteArray): ByteArray {
        val decoder = JavaBase64.getDecoder()
        return decoder.decode(input)
    }

    // endregion
}
