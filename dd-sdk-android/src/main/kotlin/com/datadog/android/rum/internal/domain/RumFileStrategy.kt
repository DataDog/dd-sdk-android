/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import java.io.File
import java.util.concurrent.ExecutorService

internal class RumFileStrategy(
    context: Context,
    filePersistenceConfig: FilePersistenceConfig =
        FilePersistenceConfig(recentDelayMs = MAX_DELAY_BETWEEN_RUM_EVENTS_MS),
    dataPersistenceExecutorService: ExecutorService
) : AsyncWriterFilePersistenceStrategy<RumEvent>(
    File(context.filesDir, RUM_FOLDER),
    RumEventSerializer(),
    filePersistenceConfig,
    PayloadDecoration.NEW_LINE_DECORATION,
    dataPersistenceExecutorService = dataPersistenceExecutorService
) {
    companion object {
        internal const val TRACES_DATA_VERSION = 1
        internal const val DATA_FOLDER_ROOT = "dd-rum"
        internal const val RUM_FOLDER = "$DATA_FOLDER_ROOT-v$TRACES_DATA_VERSION"
        internal const val MAX_DELAY_BETWEEN_RUM_EVENTS_MS = 5000L
    }
}
