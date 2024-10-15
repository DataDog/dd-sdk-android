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
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest.MatchingStrategy.CONTAINS
import com.datadog.android.sdk.integration.sessionreplay.BaseSessionReplayTest.MatchingStrategy.EXACT
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.android.sdk.utils.waitFor
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration
import org.junit.After
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

internal abstract class BaseSessionReplayTest<R : Activity> {

    @After
    fun tearDown() {
        GlobalRumMonitor.get().stopSession()
        Datadog.stopInstance()
    }

    protected fun assessSrPayload(payloadFileName: String, rule: SessionReplayTestRule<R>) {
        if (isPayloadUpdateRequest()) {
            ConditionWatcher {
                updateExpectedDataInPayload(
                    rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl),
                    payloadFileName
                )
                true
            }.doWait(timeoutMs = INITIAL_WAIT_MS)
        } else {
            ConditionWatcher {
                // verify the captured log events into the MockedWebServer
                verifyExpectedSrData(
                    rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl),
                    payloadFileName,
                    MatchingStrategy.CONTAINS
                )
                true
            }.doWait(timeoutMs = INITIAL_WAIT_MS)
        }
    }

    protected fun runInstrumentationScenario() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // we need this to avoid the Bitrise flakiness by requiring waiting for
        // SurfaceFlinger to call the onDraw method which will trigger the screen snapshot.
        Espresso.onView(ViewMatchers.isRoot()).perform(waitFor(UI_THREAD_DELAY_IN_MS))
        instrumentation.waitForIdleSync()
    }

    private fun verifyExpectedSrData(
        handledRequests: List<HandledRequest>,
        expectedPayloadFileName: String,
        matchingStrategy: MatchingStrategy = MatchingStrategy.EXACT
    ) {
        val records = handledRequests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { it.getAsJsonArray("records") }
            .map { it.dropInconsistentProperties() }
        val expectedPayload = resolveTestExpectedPayload(expectedPayloadFileName)
            .asJsonArray
            .map { it.dropInconsistentProperties() }
        val assertion = assertThat(records)
            .usingRecursiveFieldByFieldElementComparator(
                RecursiveComparisonConfiguration
                    .builder()
                    .withComparatorForType(
                        jsonPrimitivesComparator,
                        JsonPrimitive::class.java
                    ).build()
            )
        when (matchingStrategy) {
            MatchingStrategy.EXACT -> assertion.containsExactlyElementsOf(expectedPayload)
            MatchingStrategy.CONTAINS -> assertion.containsAll(expectedPayload)
        }
    }

    private fun updateExpectedDataInPayload(
        handledRequests: List<HandledRequest>,
        expectedPayloadFileName: String
    ) {
        val records = handledRequests
            .mapNotNull { it.extractSrSegmentAsJson()?.asJsonObject }
            .flatMap { it.getAsJsonArray("records") }
            // we are dropping the incremental records as they are producing noise and
            // flakiness. In the end we are only interested in full snapshot records.
            .filter {
                it.asJsonObject.get("type").asString != "11"
            }
            .map { it.dropInconsistentProperties() }
        // we make sure the payload is valid before updating it
        validatePayload(records)
        val payloadUpdateFile = resolvePayloadUpdateFile(expectedPayloadFileName)
        if (!payloadUpdateFile.exists()) {
            payloadUpdateFile.createNewFile()
        }
        // we only take the first 3 records which will contain the:
        // 1. Meta Record
        // 2. Focus Record
        // 3. Full Snapshot Record
        val payloadUpdateData = records.take(3).toPrettyString()
        payloadUpdateFile.writeText(payloadUpdateData)
    }

    private fun validatePayload(records: List<JsonObject>) {
        // the payload should contain at least 3 record
        // first record should always be a MetaRecord
        // second record should always be the FocusRecord
        assertThat(records.size).isGreaterThan(2)
        assertThat(records[0].asJsonObject.get("type").asString).isEqualTo("4")
        assertThat(records[1].asJsonObject.get("type").asString).isEqualTo("6")
    }

    private fun isPayloadUpdateRequest(): Boolean {
        return InstrumentationRegistry.getArguments().getString(PAYLOAD_UPDATE_REQUEST)
            ?.toBoolean() ?: false
    }

    private fun resolveTestExpectedPayload(fileName: String): JsonElement {
        return InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open("$PAYLOADS_FOLDER/$fileName").use {
                JsonParser.parseString(it.readBytes().toString(Charsets.UTF_8))
            }
    }

    private fun resolvePayloadUpdateFile(payloadFileName: String): File {
        return File(resolvePayloadsDirectory(), payloadFileName)
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
        while (!decompressor.finished()) {
            val resultLength = decompressor.inflate(buf)
            bos.write(buf, 0, resultLength)
        }
        decompressor.end()
        return bos.toByteArray()
    }

    private val jsonPrimitivesComparator: (o1: JsonPrimitive, o2: JsonPrimitive) -> Int =
        { o1, o2 ->
            if (comparingFloatAndLazilyParsedNumber(o1, o2)) {
                // when comparing a float with a LazilyParsedNumber the `JsonPrimitive#equals`
                // method uses Double.parseValue(value) to convert the value from the
                // LazilyParsedNumber and this method uses an extra precision. This will
                // create assertion issues because even though the original values
                // are the same the parsed values are no longer matching.
                if (o1.asString.toDouble() == o2.asString.toDouble()) {
                    0
                } else {
                    -1
                }
            } else {
                if (o1 == o2) {
                    0
                } else {
                    -1
                }
            }
        }

    private fun comparingFloatAndLazilyParsedNumber(o1: JsonPrimitive, o2: JsonPrimitive): Boolean {
        return (o1.isNumber && o2.isNumber) &&
            (o1.asNumber is Float || o2.asNumber is Float) &&
            (o1.asNumber is LazilyParsedNumber || o2.asNumber is LazilyParsedNumber)
    }

    private fun JsonElement.dropInconsistentProperties(): JsonObject {
        // We need to remove all the inconsistent properties from the payload as they will alter
        // the tests. The timestamps and ids are auto - generated and we could not predict them.
        // For the wireframes x,y and positions we need to remove them because the tests
        // will be executed in Bitrise and currently Bitrise does not own a specific model for the
        // API 33. They only have a standard emulator for this API with the required screen size and
        // X,Y positions are different from the ones we have in our local emulator.
        // Also the base64 and resourceId encoded images values are inconsistent from one run to another so will
        // be removed from the payload.

        return this.asJsonObject.apply {
            remove("timestamp")
            get("data")?.asJsonObject?.let { dataObject ->
                dataObject.get("wireframes")?.asJsonArray
                    ?.mapNotNull { wireframe ->
                        val wireframeJson = wireframe.asJsonObject
                        wireframeJson.remove("id")
                        wireframeJson.remove("x")
                        wireframeJson.remove("y")
                        wireframeJson.remove("base64")
                        wireframeJson.remove("resourceId")
                        wireframeJson
                    }?.fold(JsonArray()) { acc, jsonObject ->
                        acc.add(jsonObject)
                        acc
                    }?.let { sanitizedWireframes ->
                        dataObject.add("wireframes", sanitizedWireframes)
                    }
            }
        }
    }

    private fun List<JsonObject>.toPrettyString(): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return this.map { gson.toJson(it) }.toString()
    }

    @Suppress("UseCheckOrError")
    private fun resolvePayloadsDirectory(): File {
        return InstrumentationRegistry.getInstrumentation().targetContext.externalCacheDir?.let {
            val payloadsDir = File(it, PAYLOADS_FOLDER).apply {
                if (!this.exists()) {
                    this.mkdirs()
                }
            }
            return payloadsDir
        } ?: throw IllegalStateException("Could not resolve the payloads directory")
    }

    /**
     * The matching strategy to use when comparing the expected payload with the actual one.
     * @see EXACT will compare the payloads exactly as they are.
     * @see CONTAINS will check if the actual payload contains all the expected elements.
     */
    enum class MatchingStrategy {
        EXACT,
        CONTAINS
    }

    companion object {
        internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(30)
        private const val UI_THREAD_DELAY_IN_MS = 1000L
        private const val PAYLOAD_UPDATE_REQUEST = "updateSrPayloads"
        private val SEGMENT_FORM_DATA_REGEX =
            Regex("content-disposition: form-data; name=\"segment\"; filename=\"(.+)\"")
        private val CONTENT_LENGTH_REGEX =
            Regex("content-length: (\\d+)")
        private const val PAYLOADS_FOLDER = "session_replay_payloads"
    }
}
