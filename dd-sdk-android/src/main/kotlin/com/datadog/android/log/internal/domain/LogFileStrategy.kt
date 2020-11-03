/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.PayloadDecoration
import java.io.File
import java.util.concurrent.ExecutorService

internal class LogFileStrategy(
    context: Context,
    filePersistenceConfig: FilePersistenceConfig =
        FilePersistenceConfig(recentDelayMs = MAX_DELAY_BETWEEN_LOGS_MS),
    dataPersistenceExecutorService: ExecutorService
) : AsyncWriterFilePersistenceStrategy<Log>(
    File(context.filesDir, LOGS_FOLDER),
    LogSerializer(),
    filePersistenceConfig,
    PayloadDecoration.JSON_ARRAY_DECORATION,
    LogFileDataMigrator(context.filesDir),
    dataPersistenceExecutorService
) {
    companion object {
        internal const val LOGS_DATA_VERSION = 1
        internal const val DATA_FOLDER_ROOT = "dd-logs"
        internal const val LOGS_FOLDER = "$DATA_FOLDER_ROOT-v$LOGS_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_LOGS_MS = 5000L
    }
}
