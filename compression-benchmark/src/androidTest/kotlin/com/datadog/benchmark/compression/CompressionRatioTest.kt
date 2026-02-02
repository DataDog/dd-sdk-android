/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.compression

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import com.github.luben.zstd.ZstdOutputStream as ZstdJniOutputStream
import io.airlift.compress.v3.zstd.ZstdOutputStream as ZstdJavaOutputStream

/**
 * Test class to measure and report compression ratios for all compression algorithms.
 * 
 * This test outputs structured JSON logs that can be collected and analyzed.
 */
@RunWith(AndroidJUnit4::class)
class CompressionRatioTest {

    companion object {
        private const val TAG = "CompressionRatio"
    }

    private val payloads = mutableMapOf<String, ByteArray>()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        payloads["rum_small"] = context.assets.open("payloads/rum_small.json").readBytes()
        payloads["rum_medium"] = context.assets.open("payloads/rum_medium.json").readBytes()
        payloads["rum_large"] = context.assets.open("payloads/rum_large.json").readBytes()
        payloads["logs_batch"] = context.assets.open("payloads/logs_batch.json").readBytes()
    }

    @Test
    fun measureCompressionRatios() {
        val results = mutableListOf<CompressionResult>()

        for ((name, payload) in payloads) {
            val gzipCompressed = compressWithGzip(payload)
            val zstdJniCompressed = compressWithZstdJni(payload)
            val zstdJavaCompressed = compressWithZstdJava(payload)

            val result = CompressionResult(
                name = name,
                originalSize = payload.size,
                gzipSize = gzipCompressed.size,
                zstdJniSize = zstdJniCompressed.size,
                zstdJavaSize = zstdJavaCompressed.size
            )
            results.add(result)

            // Log each result for collection
            Log.i(TAG, result.toJson())
        }

        // Log summary
        Log.i(TAG, "=== COMPRESSION RATIO SUMMARY ===")
        for (result in results) {
            Log.i(TAG, result.toSummary())
        }

        // Log average ratios
        val avgGzipRatio = results.map { it.gzipRatio }.average()
        val avgZstdJniRatio = results.map { it.zstdJniRatio }.average()
        val avgZstdJavaRatio = results.map { it.zstdJavaRatio }.average()

        Log.i(TAG, "=== AVERAGE COMPRESSION RATIOS ===")
        Log.i(TAG, "Gzip:      %.2f%%".format(avgGzipRatio * 100))
        Log.i(TAG, "Zstd JNI:  %.2f%%".format(avgZstdJniRatio * 100))
        Log.i(TAG, "Zstd Java: %.2f%%".format(avgZstdJavaRatio * 100))

        // Write results to a file for the benchmark runner script to read
        writeResultsToFile(results)

        // Also output to instrumentation results
        val bundle = Bundle()
        bundle.putString("compression_ratios", results.joinToString("\n") { it.toJson() })
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }

    private fun writeResultsToFile(results: List<CompressionResult>) {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val outputFile = File(outputDir, "compression_ratios.json")
            
            val jsonArray = results.joinToString(",\n  ", "[\n  ", "\n]") { it.toJson() }
            outputFile.writeText(jsonArray)
            
            Log.i(TAG, "Results written to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write results to file", e)
        }
    }

    private fun compressWithGzip(data: ByteArray): ByteArray {
        val buffer = Buffer()
        val gzipSink = GzipSink(buffer).buffer()
        gzipSink.write(data)
        gzipSink.close()
        return buffer.readByteArray()
    }

    private fun compressWithZstdJni(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdJniOutputStream(outputStream)
        zstdOutputStream.write(data)
        zstdOutputStream.close()
        return outputStream.toByteArray()
    }

    private fun compressWithZstdJava(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val zstdOutputStream = ZstdJavaOutputStream(outputStream)
        zstdOutputStream.write(data)
        zstdOutputStream.close()
        return outputStream.toByteArray()
    }

    data class CompressionResult(
        val name: String,
        val originalSize: Int,
        val gzipSize: Int,
        val zstdJniSize: Int,
        val zstdJavaSize: Int
    ) {
        val gzipRatio: Double get() = gzipSize.toDouble() / originalSize
        val zstdJniRatio: Double get() = zstdJniSize.toDouble() / originalSize
        val zstdJavaRatio: Double get() = zstdJavaSize.toDouble() / originalSize

        fun toJson(): String {
            return """{"name":"$name","original":$originalSize,"gzip":$gzipSize,"zstd_jni":$zstdJniSize,"zstd_java":$zstdJavaSize}"""
        }

        fun toSummary(): String {
            return "$name: original=$originalSize, " +
                "gzip=$gzipSize (%.1f%%), ".format(gzipRatio * 100) +
                "zstd_jni=$zstdJniSize (%.1f%%), ".format(zstdJniRatio * 100) +
                "zstd_java=$zstdJavaSize (%.1f%%)".format(zstdJavaRatio * 100)
        }
    }
}
