/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.warningWithTelemetry
import com.datadog.android.privacy.TrackingConsent
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

internal class ConsentAwareFileMigrator(
    private val fileHandler: FileHandler,
    private val executorService: ExecutorService,
    private val internalLogger: Logger
) : DataMigrator<TrackingConsent> {

    @WorkerThread
    override fun migrateData(
        previousState: TrackingConsent?,
        previousFileOrchestrator: FileOrchestrator,
        newState: TrackingConsent,
        newFileOrchestrator: FileOrchestrator
    ) {
        val operation = when (previousState to newState) {
            null to TrackingConsent.PENDING,
            null to TrackingConsent.GRANTED,
            null to TrackingConsent.NOT_GRANTED,
            TrackingConsent.PENDING to TrackingConsent.NOT_GRANTED -> {
                WipeDataMigrationOperation(
                    previousFileOrchestrator.getRootDir(),
                    fileHandler,
                    internalLogger
                )
            }

            TrackingConsent.GRANTED to TrackingConsent.PENDING,
            TrackingConsent.NOT_GRANTED to TrackingConsent.PENDING -> {
                WipeDataMigrationOperation(
                    newFileOrchestrator.getRootDir(),
                    fileHandler,
                    internalLogger
                )
            }

            TrackingConsent.PENDING to TrackingConsent.GRANTED -> {
                MoveDataMigrationOperation(
                    previousFileOrchestrator.getRootDir(),
                    newFileOrchestrator.getRootDir(),
                    fileHandler,
                    internalLogger
                )
            }

            TrackingConsent.PENDING to TrackingConsent.PENDING,
            TrackingConsent.GRANTED to TrackingConsent.GRANTED,
            TrackingConsent.GRANTED to TrackingConsent.NOT_GRANTED,
            TrackingConsent.NOT_GRANTED to TrackingConsent.NOT_GRANTED,
            TrackingConsent.NOT_GRANTED to TrackingConsent.GRANTED -> {
                NoOpDataMigrationOperation()
            }

            else -> {
                sdkLogger.warningWithTelemetry(
                    "Unexpected consent migration from $previousState to $newState"
                )
                NoOpDataMigrationOperation()
            }
        }
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            executorService.submit(operation)
        } catch (e: RejectedExecutionException) {
            internalLogger.e(DataMigrator.ERROR_REJECTED, e)
        }
    }
}
