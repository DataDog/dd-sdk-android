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
import com.datadog.android.core.internal.data.privacy.Consent

internal class DefaultMigratorFactory(
    private val pendingFolderPath: String,
    private val approvedFolderPath: String
) : MigratorFactory {

    override fun resolveMigrator(
        prevConsentFlag: Consent?,
        newConsentFlag: Consent
    ): BatchedDataMigrator {

        return when (prevConsentFlag to newConsentFlag) {
            Consent.PENDING to Consent.NOT_GRANTED -> {
                WipeDataMigrator(pendingFolderPath)
            }
            Consent.PENDING to Consent.GRANTED -> {
                MoveDataMigrator(pendingFolderPath, approvedFolderPath)
            }
            else -> NoOpBatchedDataMigrator()
        }
    }
}
