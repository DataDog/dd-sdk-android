/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.profiling.workload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("MagicNumber")
internal class CpuBurstWorkload(
    private val workMs: Long = 30L,
    private val idleMs: Long = 70L
) : Workload {
    override fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val deadline = System.nanoTime() + workMs * NANOS_PER_MILLI
                var acc = 0.0
                while (System.nanoTime() < deadline) {
                    acc += sin(acc) + cos(acc) + sqrt(abs(acc) + 1.0)
                }
                blackHole(acc)
                delay(idleMs)
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
