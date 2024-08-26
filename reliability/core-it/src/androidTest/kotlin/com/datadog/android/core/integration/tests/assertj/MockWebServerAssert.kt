/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.assertj

import com.datadog.android.api.feature.stub.StubRequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.integration.tests.utils.HandledRequest
import com.datadog.android.core.integration.tests.utils.MockWebServerWrapper
import com.datadog.android.privacy.TrackingConsent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

class MockWebServerAssert(actual: MockWebServerWrapper) : AbstractAssert<MockWebServerAssert, MockWebServerWrapper>(
    actual,
    MockWebServerAssert::class.java
) {
    private var configuration: Configuration? = null
    private var trackingConsent: TrackingConsent? = null

    fun withConfiguration(configuration: Configuration): MockWebServerAssert {
        this.configuration = configuration
        return this
    }

    fun withTrackingConsent(trackingConsent: TrackingConsent): MockWebServerAssert {
        this.trackingConsent = trackingConsent
        return this
    }

    fun receivedData(data: List<RawBatchEvent>, batchMetadata: ByteArray? = null) {
        val readableExpectedData = data.map { it.toReadableRawBatchEvent(batchMetadata) }
        val interceptedRequests = actual.getHandledRequests()
        val interceptedData = resolveInterceptedData(interceptedRequests)
        assertThat(interceptedData).withFailMessage {
            """
            |MocKWebServer for core with configuration: $configuration and tracking consent: $trackingConsent
            |Expected to receive the following data:
            |$readableExpectedData
            |But only received:
            |$interceptedData
            """.trimMargin()
        }.containsAll(readableExpectedData)
    }

    fun didNotReceiveData(data: List<RawBatchEvent>, batchMetadata: ByteArray? = null) {
        val readableNotExpectedData = data.map { it.toReadableRawBatchEvent(batchMetadata) }
        val interceptedRequests = actual.getHandledRequests()
        val interceptedData = resolveInterceptedData(interceptedRequests)
        assertThat(interceptedData).withFailMessage {
            """
            |MocKWebServer for core with configuration: $configuration and tracking consent: $trackingConsent
            |Expected to not receive the following data:
            |$readableNotExpectedData
            |But only received:
            |$interceptedData
            """.trimMargin()
        }.doesNotContainAnyElementsOf(readableNotExpectedData)
    }

    private fun resolveInterceptedData(interceptedRequests: List<HandledRequest>) =
        interceptedRequests.mapNotNull { request ->
            request.jsonBody?.asJsonArray
        }.flatMap { jsonArray ->
            jsonArray.map { it.asJsonObject }
        }.map { jsonObject ->
            val eventData = jsonObject.get(StubRequestFactory.DATA_KEY).asString
            val eventMetadata = jsonObject.get(StubRequestFactory.METADATA_KEY).asString
            val batchMetadata = jsonObject.get(StubRequestFactory.BATCH_METADATA)?.asString
            ReadableRawBatchEvent(
                eventData,
                eventMetadata,
                batchMetadata
            )
        }

    private fun RawBatchEvent.toReadableRawBatchEvent(batchMetadata: ByteArray?): ReadableRawBatchEvent {
        return ReadableRawBatchEvent(
            data = data.decodeToString(),
            metadata = metadata.decodeToString(),
            batchMetadata = batchMetadata?.decodeToString()
        )
    }

    private data class ReadableRawBatchEvent(
        val data: String,
        val metadata: String,
        val batchMetadata: String? = null
    )

    companion object {
        fun assertThat(actual: MockWebServerWrapper): MockWebServerAssert {
            return MockWebServerAssert(actual)
        }
    }
}
