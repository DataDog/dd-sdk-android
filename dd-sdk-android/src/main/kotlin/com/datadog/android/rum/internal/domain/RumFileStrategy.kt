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
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import java.io.File
import java.util.concurrent.ExecutorService

internal class RumFileStrategy(
    context: Context,
    filePersistenceConfig: FilePersistenceConfig =
        FilePersistenceConfig(recentDelayMs = MAX_DELAY_BETWEEN_RUM_EVENTS_MS),
    dataPersistenceExecutorService: ExecutorService,
    trackingConsentProvider: ConsentProvider
) : FilePersistenceStrategy<RumEvent>(
    File(context.filesDir, INTERMEDIATE_DATA_FOLDER),
    File(context.filesDir, AUTHORIZED_FOLDER),
    RumEventSerializer(),
    dataPersistenceExecutorService,
    filePersistenceConfig,
    PayloadDecoration.NEW_LINE_DECORATION,
    trackingConsentProvider
) {
    companion object {
        internal const val VERSION = 1
        internal const val ROOT = "dd-rum"
        internal const val INTERMEDIATE_DATA_FOLDER =
            "$ROOT-pending-v$VERSION"
        internal const val AUTHORIZED_FOLDER = "$ROOT-v$VERSION"
        internal const val MAX_DELAY_BETWEEN_RUM_EVENTS_MS = 5000L
    }
}
