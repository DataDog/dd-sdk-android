/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.domain.batching.migrators.BatchedDataMigrator
import com.datadog.android.core.internal.domain.batching.migrators.MoveDataMigrator
import com.datadog.android.core.internal.domain.batching.migrators.NoOpBatchedDataMigrator
import com.datadog.android.core.internal.domain.batching.migrators.WipeDataMigrator
import com.datadog.android.privacy.TrackingConsent
import java.util.concurrent.ExecutorService

internal class DefaultMigratorFactory(
    private val pendingFolderPath: String,
    private val approvedFolderPath: String,
    private val executorService: ExecutorService
) : MigratorFactory {

    override fun resolveMigrator(
        prevConsentFlag: TrackingConsent?,
        newConsentFlag: TrackingConsent
    ): BatchedDataMigrator {

        return when (prevConsentFlag to newConsentFlag) {
            TrackingConsent.PENDING to TrackingConsent.NOT_GRANTED -> {
                WipeDataMigrator(pendingFolderPath, executorService)
            }
            TrackingConsent.PENDING to TrackingConsent.GRANTED -> {
                MoveDataMigrator(pendingFolderPath, approvedFolderPath, executorService)
            }
            // We need this to make sure we clear the current folder when initializing the SDK
            null to TrackingConsent.PENDING,
            TrackingConsent.GRANTED to TrackingConsent.PENDING,
            TrackingConsent.NOT_GRANTED to TrackingConsent.PENDING -> {
                WipeDataMigrator(pendingFolderPath, executorService)
            }
            else -> NoOpBatchedDataMigrator()
        }
    }
}
