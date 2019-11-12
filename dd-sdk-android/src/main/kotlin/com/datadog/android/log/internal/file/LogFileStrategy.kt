/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import com.datadog.android.log.internal.LogReader
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogWriter
import java.io.File

internal class LogFileStrategy(private val rootDir: File) : LogStrategy {

    constructor(context: Context) :
        this(File(context.filesDir, LogFileWriter.LOGS_FOLDER_NAME))

    // region LogPersistingStrategy

    override fun getLogWriter(): LogWriter {
        return LogFileWriter(rootDir)
    }

    override fun getLogReader(): LogReader {
        return LogFileReader(rootDir)
    }

    // endregion
}
