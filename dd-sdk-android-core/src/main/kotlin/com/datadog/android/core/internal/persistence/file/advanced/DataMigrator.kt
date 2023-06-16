/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileOrchestrator

internal interface DataMigrator<S : Any> {

    @WorkerThread
    fun migrateData(
        previousState: S?,
        previousFileOrchestrator: FileOrchestrator,
        newState: S,
        newFileOrchestrator: FileOrchestrator
    )

    companion object {
        internal const val ERROR_REJECTED = "Unable to schedule migration on the executor"
    }
}
