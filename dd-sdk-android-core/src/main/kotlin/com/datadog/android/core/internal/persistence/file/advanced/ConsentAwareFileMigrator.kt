/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.InternalLogger

internal class ConsentAwareFileMigrator(
    private val fileMover: FileMover,
    private val internalLogger: InternalLogger
) : DataMigrator<TrackingConsent> {

    @WorkerThread
    override fun migrateData(
        previousState: TrackingConsent?,
        previousFileOrchestrator: FileOrchestrator,
        newState: TrackingConsent,
        newFileOrchestrator: FileOrchestrator
    ) {
        val operation = resolveMigrationOperation(
            previousState,
            newState,
            previousFileOrchestrator,
            newFileOrchestrator
        )
        operation.run()
    }

    @WorkerThread
    private fun resolveMigrationOperation(
        previousState: TrackingConsent?,
        newState: TrackingConsent,
        previousFileOrchestrator: FileOrchestrator,
        newFileOrchestrator: FileOrchestrator
    ) = when (previousState to newState) {
        null to TrackingConsent.PENDING,
        null to TrackingConsent.GRANTED,
        null to TrackingConsent.NOT_GRANTED,
        TrackingConsent.PENDING to TrackingConsent.NOT_GRANTED -> {
            WipeDataMigrationOperation(
                previousFileOrchestrator.getRootDir(),
                fileMover,
                internalLogger
            )
        }

        TrackingConsent.GRANTED to TrackingConsent.PENDING,
        TrackingConsent.NOT_GRANTED to TrackingConsent.PENDING -> {
            WipeDataMigrationOperation(
                newFileOrchestrator.getRootDir(),
                fileMover,
                internalLogger
            )
        }

        TrackingConsent.PENDING to TrackingConsent.GRANTED -> {
            MoveDataMigrationOperation(
                previousFileOrchestrator.getRootDir(),
                newFileOrchestrator.getRootDir(),
                fileMover,
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
            internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(
                    InternalLogger.Target.MAINTAINER,
                    InternalLogger.Target.TELEMETRY
                ),
                { "Unexpected consent migration from $previousState to $newState" }
            )
            NoOpDataMigrationOperation()
        }
    }
}
