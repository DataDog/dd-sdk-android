/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay.textandinputprivacy

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.waitFor
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

internal abstract class TextAndInputPrivacyTestBase<R : Activity> {

    @After
    fun tearDown() {
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()
    }

    protected fun runInstrumentationScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .perform(waitFor(UI_THREAD_DELAY_IN_MS))
        instrumentation.waitForIdleSync()
    }

    protected fun extractTextWireframesFromRequests(requests: List<HandledRequest>): List<JsonObject> {
        return extractRecordsFromRequests(requests)
            .filter { it.get("type")?.asString == "10" }
            .flatMap { record ->
                val data = record.asJsonObject.get("data")?.asJsonObject
                data?.getAsJsonArray("wireframes") ?: JsonArray()
            }
            .map { it.asJsonObject }
            .filter { it.get("type")?.asString == "text" }
    }

    protected fun assertStaticTextVisible(
        rule: SessionReplayTestRule<R>,
        expectedText: String
    ) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val textWireframes = extractTextWireframesFromRequests(requests)

            val staticTextWireframe = textWireframes.firstOrNull { wireframe ->
                val text = wireframe.get("text")?.asString
                text == expectedText
            }

            assertThat(staticTextWireframe)
                .describedAs("Static text should be visible with text: $expectedText")
                .isNotNull

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertStaticTextMasked(
        rule: SessionReplayTestRule<R>,
        originalText: String
    ) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val textWireframes = extractTextWireframesFromRequests(requests)

            val maskedTextWireframe = textWireframes.firstOrNull { wireframe ->
                val text = wireframe.get("text")?.asString
                text != null && text != originalText && text.isNotEmpty()
            }

            assertThat(maskedTextWireframe)
                .describedAs("Static text should be masked (not equal to original: $originalText)")
                .isNotNull

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertInputTextVisible(
        rule: SessionReplayTestRule<R>,
        expectedText: String
    ) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val textWireframes = extractTextWireframesFromRequests(requests)

            val inputTextWireframe = textWireframes.firstOrNull { wireframe ->
                val text = wireframe.get("text")?.asString
                text == expectedText
            }

            assertThat(inputTextWireframe)
                .describedAs("Input text should be visible with text: $expectedText")
                .isNotNull

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertInputTextMaskedWithFixedMask(rule: SessionReplayTestRule<R>) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val textWireframes = extractTextWireframesFromRequests(requests)

            val maskedInputWireframe = textWireframes.firstOrNull { wireframe ->
                val text = wireframe.get("text")?.asString
                text == "***"
            }

            assertThat(maskedInputWireframe)
                .describedAs("Input should be masked with fixed mask: ***")
                .isNotNull

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    protected fun assertSensitiveInputMasked(rule: SessionReplayTestRule<R>) {
        ConditionWatcher {
            val requests = rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)
            val textWireframes = extractTextWireframesFromRequests(requests)

            val sensitiveWireframes = textWireframes.filter { wireframe ->
                val text = wireframe.get("text")?.asString
                text == "***"
            }

            assertThat(sensitiveWireframes)
                .describedAs("Sensitive inputs (password, email, phone) should be masked with ***")
                .hasSizeGreaterThanOrEqualTo(3)

            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    private fun extractRecordsFromRequests(requests: List<HandledRequest>): List<JsonObject> {
        return requests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { it.getAsJsonArray("records") }
            .map { it.asJsonObject }
    }

    private fun HandledRequest.extractSrSegmentAsJson(): com.google.gson.JsonElement? {
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

