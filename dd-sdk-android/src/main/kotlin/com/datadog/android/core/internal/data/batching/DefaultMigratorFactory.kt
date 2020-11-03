/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.batching

import com.datadog.android.core.internal.data.batching.migrators.BatchedDataMigrator
import com.datadog.android.core.internal.data.batching.migrators.MoveDataMigrator
import com.datadog.android.core.internal.data.batching.migrators.NoOpBatchedDataMigrator
import com.datadog.android.core.internal.data.batching.migrators.WipeDataMigrator
import com.datadog.android.privacy.TrackingConsent

internal class DefaultMigratorFactory(
    private val pendingFolderPath: String,
    private val approvedFolderPath: String
) : MigratorFactory {

    override fun resolveMigrator(
        prevConsentFlag: TrackingConsent?,
        newConsentFlag: TrackingConsent
    ): BatchedDataMigrator {

        return when (prevConsentFlag to newConsentFlag) {
            TrackingConsent.PENDING to TrackingConsent.NOT_GRANTED -> {
                WipeDataMigrator(pendingFolderPath)
            }
            TrackingConsent.PENDING to TrackingConsent.GRANTED -> {
                MoveDataMigrator(pendingFolderPath, approvedFolderPath)
            }
            else -> NoOpBatchedDataMigrator()
        }
    }
}
