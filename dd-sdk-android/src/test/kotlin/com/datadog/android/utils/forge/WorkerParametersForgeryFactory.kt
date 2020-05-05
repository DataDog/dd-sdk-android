/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import android.content.Context
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class WorkerParametersForgeryFactory : ForgeryFactory<WorkerParameters> {

    // region ForgeryFactory

    override fun getForgery(forge: Forge): WorkerParameters {
        val threadExecutor = Executors.newSingleThreadExecutor()
        return WorkerParameters(
            forge.getForgery<UUID>(),
            Data.EMPTY,
            forge.aList { anAlphabeticalString() },
            WorkerParameters.RuntimeExtras(),
            forge.aSmallInt(),
            threadExecutor,
            object : TaskExecutor {
                override fun getMainThreadExecutor(): Executor {
                    TODO()
                }

                override fun postToMainThread(runnable: Runnable?) {
                }

                override fun executeOnBackgroundThread(runnable: Runnable?) {
                }

                override fun getBackgroundExecutor(): SerialExecutor {
                    TODO()
                }
            },
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? {
                    return null
                }
            },
            ProgressUpdater { context, id, data -> forge.getForgery() },
            ForegroundUpdater { context, id, foregroundInfo -> forge.getForgery() }
        )
    }

    // endregion
}
