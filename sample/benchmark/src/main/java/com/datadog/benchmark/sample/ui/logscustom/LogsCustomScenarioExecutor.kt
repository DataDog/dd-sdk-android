/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.logscustom

import android.util.Log
import com.datadog.benchmark.sample.ui.LogPayloadSize
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

internal class LogsCustomScenarioExecutor(
    private val logsCustomScenarioViewModel: LogsScreenViewModel,
) {
    suspend fun execute() {
        // step 1
        step("Select large payload size") {
            logsCustomScenarioViewModel.dispatch(LogsScreenAction.SelectPayloadSize(LogPayloadSize.Large))
        }

        repeat(4) {
            step("Decrease logging interval") {
                logsCustomScenarioViewModel.dispatch(LogsScreenAction.DecreaseLoggingInterval)
            }
        }

        step("repeat logging") {
            logsCustomScenarioViewModel.dispatch(LogsScreenAction.SetRepeatLogging(true))
        }

        logFor30Seconds()

        // step 2
        step("Select small payload size") {
            logsCustomScenarioViewModel.dispatch(LogsScreenAction.SelectPayloadSize(LogPayloadSize.Small))
        }

        repeat(4) {
            step("increase logs per batch") {
                logsCustomScenarioViewModel.dispatch(LogsScreenAction.IncreaseLoggingSpeed)
            }
        }

        step("select debug log level") {
            logsCustomScenarioViewModel.dispatch(LogsScreenAction.SelectLogLevel(Log.DEBUG))
        }

        logFor30Seconds()

        Log.w("WAHAHA", "DONE")
    }

    private suspend fun logFor30Seconds() {
        step("start logging") {
            logsCustomScenarioViewModel.dispatch(LogsScreenAction.LogButtonClicked)
        }

        delay(30.seconds)

        step("stop logging") {
            logsCustomScenarioViewModel.dispatch(LogsScreenAction.LogButtonClicked)
        }
    }

    private suspend fun step(name: String, block: () -> Unit) {
        Log.w("WAHAHA", "Step $name")
        block()
        delay(1000)
    }
}
