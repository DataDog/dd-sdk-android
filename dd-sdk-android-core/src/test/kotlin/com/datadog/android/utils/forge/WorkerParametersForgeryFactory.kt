/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.concurrent.Executor

class WorkerParametersForgeryFactory : ForgeryFactory<WorkerParameters> {

    // region ForgeryFactory

    override fun getForgery(forge: Forge): WorkerParameters {
        val sameThreadExecutor = object : Executor {
            override fun execute(command: Runnable) = command.run()
        }
        return WorkerParameters(
            forge.getForgery(),
            Data.EMPTY,
            forge.aList { anAlphabeticalString() },
            WorkerParameters.RuntimeExtras(),
            forge.aSmallInt(),
            forge.aSmallInt(),
            sameThreadExecutor,
            object : TaskExecutor {
                override fun getMainThreadExecutor(): Executor {
                    TODO()
                }

                override fun getSerialTaskExecutor(): SerialExecutor {
                    TODO("Not yet implemented")
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
            { _, _, _ -> forge.getForgery() },
            { _, _, _ -> forge.getForgery() }
        )
    }

    // endregion
}
