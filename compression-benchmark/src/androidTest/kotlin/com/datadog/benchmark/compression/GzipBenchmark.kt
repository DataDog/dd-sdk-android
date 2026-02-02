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
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmarks for Gzip compression using OkIO's GzipSink.
 * 
 * This is the compression method currently used by the Datadog SDK.
 */
@RunWith(AndroidJUnit4::class)
class GzipBenchmark {

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
    fun compress_smallPayload_gzip() {
        benchmarkRule.measureRepeated {
            compressWithGzip(payloadSmall)
        }
    }

    @Test
    fun compress_mediumPayload_gzip() {
        benchmarkRule.measureRepeated {
            compressWithGzip(payloadMedium)
        }
    }

    @Test
    fun compress_largePayload_gzip() {
        benchmarkRule.measureRepeated {
            compressWithGzip(payloadLarge)
        }
    }

    @Test
    fun compress_logsPayload_gzip() {
        benchmarkRule.measureRepeated {
            compressWithGzip(payloadLogs)
        }
    }

    private fun compressWithGzip(data: ByteArray): ByteArray {
        val buffer = Buffer()
        val gzipSink = GzipSink(buffer).buffer()
        gzipSink.write(data)
        gzipSink.close()
        return buffer.readByteArray()
    }
}
