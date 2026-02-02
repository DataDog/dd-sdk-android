/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.compression

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.luben.zstd.ZstdOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Benchmarks for Zstd compression using the JNI-based :zstd module.
 * 
 * This uses native C code through JNI for compression.
 */
@RunWith(AndroidJUnit4::class)
class ZstdJniBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var payloadSmall: ByteArray
    private lateinit var payloadMedium: ByteArray
    private lateinit var payloadLarge: ByteArray
    private lateinit var payloadLogs: ByteArray

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        payloadSmall = context.assets.open("payloads/rum_small.json").readBytes()
        payloadMedium = context.assets.open("payloads/rum_medium.json").readBytes()
        payloadLarge = context.assets.open("payloads/rum_large.json").readBytes()
        payloadLogs = context.assets.open("payloads/logs_batch.json").readBytes()
    }

    @Test
    fun compress_smallPayload_zstdJni() {
        benchmarkRule.measureRepeated {
            compressWithZstdJni(payloadSmall)
        }
    }

    @Test
    fun compress_mediumPayload_zstdJni() {
        benchmarkRule.measureRepeated {
            compressWithZstdJni(payloadMedium)
        }
    }

    @Test
    fun compress_largePayload_zstdJni() {
        benchmarkRule.measureRepeated {
            compressWithZstdJni(payloadLarge)
        }
    }

    @Test
    fun compress_logsPayload_zstdJni() {
        benchmarkRule.measureRepeated {
            compressWithZstdJni(payloadLogs)
        }
    }

    private fun compressWithZstdJni(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdOutputStream(outputStream)
        zstdOutputStream.write(data)
        zstdOutputStream.close()
        return outputStream.toByteArray()
    }
}
