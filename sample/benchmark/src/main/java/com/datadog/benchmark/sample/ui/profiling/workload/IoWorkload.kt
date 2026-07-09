/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.profiling.workload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

@Suppress("MagicNumber")
internal class IoWorkload(
    private val cacheDir: File,
    private val periodMs: Long = 200L,
    private val payloadBytes: Int = 4 * 1024
) : Workload {
    override fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val file = File(cacheDir, "benchmark_workload.tmp")
            val payload = ByteArray(payloadBytes).also { Random.nextBytes(it) }
            try {
                while (isActive) {
                    file.writeBytes(payload)
                    blackHole(file.readBytes().size)
                    delay(periodMs)
                }
            } finally {
                file.delete()
            }
        }
    }
}
