/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import android.content.Context
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.batch.BatchFileHandler
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.log.Logger
import com.datadog.android.privacy.TrackingConsent
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

internal class FeatureFileOrchestrator(
    consentProvider: ConsentProvider,
    pendingOrchestrator: FileOrchestrator,
    grantedOrchestrator: FileOrchestrator,
    dataMigrator: DataMigrator<TrackingConsent>
) : ConsentAwareFileOrchestrator(
    consentProvider,
    pendingOrchestrator,
    grantedOrchestrator,
    dataMigrator
) {

    constructor(
        consentProvider: ConsentProvider,
        context: Context,
        featureName: String,
        executorService: ExecutorService,
        internalLogger: Logger
    ) : this(
        consentProvider,
        BatchFileOrchestrator(
            File(context.cacheDir, PENDING_DIR.format(Locale.US, featureName)),
            PERSISTENCE_CONFIG,
            internalLogger
        ),
        BatchFileOrchestrator(
            File(context.cacheDir, GRANTED_DIR.format(Locale.US, featureName)),
            PERSISTENCE_CONFIG,
            internalLogger
        ),
        ConsentAwareFileMigrator(
            BatchFileHandler(internalLogger),
            executorService,
            internalLogger
        )
    )

    companion object {
        internal const val VERSION = 2
        internal const val PENDING_DIR = "dd-%s-pending-v$VERSION"
        internal const val GRANTED_DIR = "dd-%s-v$VERSION"

        private val PERSISTENCE_CONFIG = FilePersistenceConfig()
    }
}
