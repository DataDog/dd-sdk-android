/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.data.file.RumFileWriter
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import java.io.File
import java.util.concurrent.ExecutorService

internal class RumFileStrategy(
    context: Context,
    filePersistenceConfig: FilePersistenceConfig,
    dataPersistenceExecutorService: ExecutorService,
    trackingConsentProvider: ConsentProvider,
    eventMapper: EventMapper<RumEvent>
) : FilePersistenceStrategy<RumEvent>(
    File(context.filesDir, INTERMEDIATE_DATA_FOLDER),
    File(context.filesDir, AUTHORIZED_FOLDER),
    RumEventSerializer(),
    dataPersistenceExecutorService,
    filePersistenceConfig,
    PayloadDecoration.NEW_LINE_DECORATION,
    trackingConsentProvider,
    eventMapper,
    fileWriterFactory = { fileOrchestrator, eventSerializer, eventSeparator ->
        RumFileWriter(
            File(context.filesDir, DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME),
            fileOrchestrator,
            eventSerializer,
            eventSeparator
        )
    }
) {
    companion object {
        internal const val VERSION = 1
        internal const val ROOT = "dd-rum"
        internal const val INTERMEDIATE_DATA_FOLDER =
            "$ROOT-pending-v$VERSION"
        internal const val AUTHORIZED_FOLDER = "$ROOT-v$VERSION"
    }
}
