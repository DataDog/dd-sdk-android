/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.app.Activity
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.utils.waitFor
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

internal abstract class BaseSessionReplayTest<R : Activity> {

    @After
    fun tearDown() {
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()
    }

    protected fun extractRecordsFromRequests(handledRequests: List<HandledRequest>): List<JsonObject> {
        return handledRequests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { it.getAsJsonArray("records") }
            .map { it.asJsonObject }
    }

    protected fun extractWireframesFromRequests(handledRequests: List<HandledRequest>): List<JsonObject> {
        return extractRecordsFromRequests(handledRequests)
            .filter { it.get("type")?.asString == "10" }
            .flatMap { record ->
                val data = record.asJsonObject.get("data")?.asJsonObject
                data?.getAsJsonArray("wireframes") ?: JsonArray()
            }
            .map { it.asJsonObject }
    }

    protected fun assertRecordStructure(records: List<JsonObject>) {
        assertThat(records)
            .describedAs("Session Replay should capture records")
            .isNotEmpty

        val metaRecord = records.firstOrNull { it.get("type")?.asString == "4" }
        val focusRecord = records.firstOrNull { it.get("type")?.asString == "6" }
        val fullSnapshotRecord = records.firstOrNull { it.get("type")?.asString == "10" }

        assertThat(metaRecord)
            .describedAs("Should contain a meta record (type 4)")
            .isNotNull

        assertThat(focusRecord)
            .describedAs("Should contain a focus record (type 6)")
            .isNotNull

        assertThat(fullSnapshotRecord)
            .describedAs("Should contain a full snapshot record (type 10)")
            .isNotNull
    }

    protected fun runInstrumentationScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // we need this to avoid the Bitrise flakiness by requiring waiting for
        // SurfaceFlinger to call the onDraw method which will trigger the screen snapshot.
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_IN_MS))
        instrumentation.waitForIdleSync()
    }

    private fun HandledRequest.extractSrSegmentAsJson(): JsonElement? {
        val compressedSegmentBody = resolveSrSegmentBodyFromRequest(requestBuffer.clone())
        if (compressedSegmentBody.isNotEmpty()) {
            // decompress the segment binary
            return JsonParser.parseString(String(decompressBytes(compressedSegmentBody)))
        }

        return null
    }

    @Suppress("NestedBlockDepth")
    private fun resolveSrSegmentBodyFromRequest(buffer: Buffer): ByteArray {
        // Example of a multipart form segment body:
        // Content-Disposition: form-data; name="segment"; filename="db081a08-96ab-4931-a98e-b2dd2d9c1b34"
        // Content-Type: application/octet-stream
        // Content-Length: 1060
        // compressed segment as byte array of 1060 length
        var line = buffer.readUtf8Line()
        while (line != null) {
            if (line.lowercase(Locale.ENGLISH).matches(SEGMENT_FORM_DATA_REGEX)) {
                // skip next line
                buffer.readUtf8Line()
                val contentLengthLine = buffer.readUtf8Line()?.lowercase(Locale.ENGLISH)
                if (contentLengthLine != null) {
                    val matcher = CONTENT_LENGTH_REGEX.find(contentLengthLine, 0)
                    if (matcher?.groupValues != null) {
                        // skip the next empty line
                        val contentLength = matcher.groupValues[1].toLong()
                        buffer.readUtf8Line()
                        return buffer.readByteArray(contentLength)
                    }
                }
            }
            line = buffer.readUtf8Line()
        }
        return ByteArray(0)
    }

    private fun decompressBytes(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        val decompressor = Inflater()
        decompressor.setInput(input, 0, input.size)
        var uncompressedBytes = Int.MAX_VALUE
        while (uncompressedBytes > 0) {
            uncompressedBytes = decompressor.inflate(buf)
            bos.write(buf, 0, uncompressedBytes)
        }
        decompressor.end()
        return bos.toByteArray()
    }

    companion object {
        internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
        private const val UI_THREAD_DELAY_IN_MS = 1000L
        private val SEGMENT_FORM_DATA_REGEX =
            Regex("content-disposition: form-data; name=\"segment\"; filename=\"(.+)\"")
        private val CONTENT_LENGTH_REGEX =
            Regex("content-length: (\\d+)")
    }
}
