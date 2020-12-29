/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.content.Context
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.FilePersistenceStrategy
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.privacy.ConsentProvider
import java.io.File
import java.util.concurrent.ExecutorService

internal class LogFileStrategy(
    context: Context,
    filePersistenceConfig: FilePersistenceConfig,
    dataPersistenceExecutorService: ExecutorService,
    trackingConsentProvider: ConsentProvider
) : FilePersistenceStrategy<Log>(
    File(context.filesDir, INTERMEDIATE_DATA_FOLDER),
    File(context.filesDir, AUTHORIZED_FOLDER),
    LogSerializer(),
    dataPersistenceExecutorService,
    filePersistenceConfig,
    PayloadDecoration.JSON_ARRAY_DECORATION,
    trackingConsentProvider
) {
    companion object {
        internal const val VERSION = 1
        internal const val ROOT = "dd-logs"
        internal const val INTERMEDIATE_DATA_FOLDER =
            "$ROOT-pending-v$VERSION"
        internal const val AUTHORIZED_FOLDER = "$ROOT-v$VERSION"
    }
}
