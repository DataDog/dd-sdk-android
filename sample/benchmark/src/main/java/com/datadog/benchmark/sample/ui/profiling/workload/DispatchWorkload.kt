/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.profiling.workload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@Suppress("MagicNumber")
internal class DispatchWorkload(
    private val periodMs: Long = 10L,
    private val parallelism: Int = 4
) : Workload {
    override fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                repeat(parallelism) {
                    launch(Dispatchers.Default) {
                        var x = 0.0
                        for (i in 0 until 500) x += sqrt(i.toDouble())
                        blackHole(x)
                    }
                }
                delay(periodMs)
            }
        }
    }
}
