/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.util.Log as AndroidLog
import com.datadog.android.log.Log
import com.datadog.android.log.internal.LogWriter
import java.io.File

internal class LogFileWriter(private val rootDirectory: File) : LogWriter {

    // region LoggerWriter

    override fun writeLog(log: Log) {
        TODO()
    }

    // endregion

}
