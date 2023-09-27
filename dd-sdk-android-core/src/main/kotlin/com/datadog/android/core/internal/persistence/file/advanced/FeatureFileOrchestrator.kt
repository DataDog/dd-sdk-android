/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

internal class FeatureFileOrchestrator(
    consentProvider: ConsentProvider,
    pendingOrchestrator: FileOrchestrator,
    grantedOrchestrator: FileOrchestrator,
    dataMigrator: DataMigrator<TrackingConsent>,
    executorService: ExecutorService,
    internalLogger: InternalLogger
) : ConsentAwareFileOrchestrator(
    consentProvider,
    pendingOrchestrator,
    grantedOrchestrator,
    dataMigrator,
    executorService,
    internalLogger
) {

    constructor(
        consentProvider: ConsentProvider,
        storageDir: File,
        featureName: String,
        executorService: ExecutorService,
        filePersistenceConfig: FilePersistenceConfig,
        internalLogger: InternalLogger,
        metricsDispatcher: MetricsDispatcher
    ) : this(
        consentProvider,
        BatchFileOrchestrator(
            File(storageDir, PENDING_DIR.format(Locale.US, featureName)),
            filePersistenceConfig,
            internalLogger,
            metricsDispatcher
        ),
        BatchFileOrchestrator(
            File(storageDir, GRANTED_DIR.format(Locale.US, featureName)),
            filePersistenceConfig,
            internalLogger,
            metricsDispatcher
        ),
        ConsentAwareFileMigrator(
            FileMover(internalLogger),
            internalLogger
        ),
        executorService,
        internalLogger
    )

    companion object {
        private const val BASE_DIR_NAME_REG_EX = "([a-z]+-)+"
        internal val IS_GRANTED_DIR_REG_EX = Regex("${BASE_DIR_NAME_REG_EX}v[0-9]+")
        internal val IS_PENDING_DIR_REG_EX = Regex("${BASE_DIR_NAME_REG_EX}pending-v[0-9]+")

        internal const val VERSION = 2
        internal const val PENDING_DIR = "%s-pending-v$VERSION"
        internal const val GRANTED_DIR = "%s-v$VERSION"
    }
}
