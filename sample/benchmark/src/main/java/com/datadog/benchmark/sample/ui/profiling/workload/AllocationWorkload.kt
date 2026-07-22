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
import kotlin.random.Random

@Suppress("MagicNumber")
internal class AllocationWorkload(
    private val periodMs: Long = 50L,
    private val bufferBytes: Int = 32 * 1024,
    private val listSize: Int = 256
) : Workload {
    override fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val buffer = ByteArray(bufferBytes).also { Random.nextBytes(it) }
                val list = List(listSize) { "alloc-$it-${Random.nextInt()}" }
                blackHole(buffer.size + list.size)
                delay(periodMs)
            }
        }
    }
}
