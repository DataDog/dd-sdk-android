/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.touchprivacy

import android.app.Activity
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
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

internal abstract class TouchPrivacyTestBase<R : Activity> {

    @After
    fun tearDown() {
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()
    }

    protected fun runTouchScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        
        Espresso.onView(ViewMatchers.withId(com.datadog.android.sdk.integration.R.id.button1))
            .perform(ViewActions.click())
        
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_IN_MS))
        instrumentation.waitForIdleSync()
    }

    protected fun assertTouchRecordsExist(rule: SessionReplayTestRule<R>) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val touchRecords = extractTouchRecordsFromRequests(requests)
            
            assertThat(touchRecords)
                .describedAs("Should capture touch interaction records with SHOW privacy")
                .isNotEmpty
            
            touchRecords.forEach { record ->
                val data = record.get("data")?.asJsonObject
                assertThat(data).isNotNull
                
                val pointerType = data?.get("pointerType")?.asString
                val x = data?.get("x")?.asLong
                val y = data?.get("y")?.asLong
                
                assertThat(pointerType)
                    .describedAs("Pointer type should be TOUCH")
                    .isEqualToIgnoringCase("touch")
                
                assertThat(x).isNotNull.isGreaterThanOrEqualTo(0)
                assertThat(y).isNotNull.isGreaterThanOrEqualTo(0)
            }
            
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertNoTouchRecords(rule: SessionReplayTestRule<R>) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val touchRecords = extractTouchRecordsFromRequests(requests)
            
            assertThat(touchRecords)
                .describedAs("Should NOT capture touch records with HIDE privacy")
                .isEmpty()
            
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private fun extractTouchRecordsFromRequests(requests: List<HandledRequest>): List<JsonObject> {
        return requests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { segment ->
                segment.getAsJsonArray("records")
                    ?.filter { record ->
                        val recordObj = record.asJsonObject
                        val type = recordObj.get("type")?.asString
                        type == "11"
                    }
                    ?.filter { record ->
                        val data = record.asJsonObject.get("data")?.asJsonObject
                        data?.has("pointerType") == true
                    }
                    ?.map { it.asJsonObject }
                    ?: emptyList()
            }
    }

    private fun HandledRequest.extractSrSegmentAsJson(): JsonElement? {
        val compressedSegmentBody = resolveSrSegmentBodyFromRequest(requestBuffer.clone())
        if (compressedSegmentBody.isNotEmpty()) {
            return JsonParser.parseString(String(decompressBytes(compressedSegmentBody)))
        }
        return null
    }

    @Suppress("NestedBlockDepth")
    private fun resolveSrSegmentBodyFromRequest(buffer: Buffer): ByteArray {
        var line = buffer.readUtf8Line()
        while (line != null) {
            if (line.lowercase(Locale.ENGLISH).matches(SEGMENT_FORM_DATA_REGEX)) {
                buffer.readUtf8Line()
                val contentLengthLine = buffer.readUtf8Line()?.lowercase(Locale.ENGLISH)
                if (contentLengthLine != null) {
                    val matcher = CONTENT_LENGTH_REGEX.find(contentLengthLine, 0)
                    if (matcher?.groupValues != null) {
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

