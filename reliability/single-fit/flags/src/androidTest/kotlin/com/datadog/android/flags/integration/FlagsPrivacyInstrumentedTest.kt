/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import androidx.test.core.app.ApplicationProvider
import com.datadog.android.Datadog
import com.datadog.android._InternalProxy
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.openfeature.asOpenFeatureProvider
import com.datadog.android.privacy.TrackingConsent
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.Value
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.GzipSource
import okio.buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Runs the release SDK and provider on ART with real disk storage, scheduling and HTTP uploads. */
@RunWith(Parameterized::class)
internal class FlagsPrivacyInstrumentedTest(private val useOpenFeature: Boolean) {
    private val server = MockWebServer()
    private val assignmentBody = AtomicReference(assignments(false))
    private val assignmentStatus = AtomicInteger(200)
    private val retryUploads = AtomicInteger(0)
    private val uploads = LinkedBlockingQueue<String>()
    private val assignmentRequests = LinkedBlockingQueue<String>()
    private val exposureCount = AtomicInteger(0)
    private val clientName = UUID.randomUUID().toString()
    private lateinit var sdkCore: FeatureSdkCore
    private lateinit var client: FlagsClient

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/assignments" -> {
                    assignmentRequests.add(body(request))
                    MockResponse().setResponseCode(assignmentStatus.get()).setBody(assignmentBody.get())
                }
                "/evaluations" -> {
                    uploads.add(body(request))
                    MockResponse().setResponseCode(if (retryUploads.getAndDecrement() > 0) 503 else 202)
                }
                "/exposures" -> {
                    exposureCount.incrementAndGet()
                    MockResponse().setResponseCode(202)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        runBlocking { OpenFeatureAPI.shutdown() }
        if (::sdkCore.isInitialized) {
            flushOnStop()
            Datadog.stopInstance()
        }
        server.shutdown()
    }

    @Test
    fun mustProtectConsentAndErrors_whenRefreshBeforeShutdown() {
        // Given
        startSdk()
        val consentValues = listOf(null, false, JSONObject.NULL, "true", true, false)

        // When
        consentValues.forEach { consent ->
            assignmentBody.set(assignments(consent))
            refresh()
            repeat(2) { assertTrue(booleanValue("enabled")) }
            assertFalse(booleanValue("missing"))
            assertFalse(booleanValue("wrong-type"))
            assertEquals(0, integerValue("broken"))
        }
        flushOnStop()
        val wire = takeUpload()
        val events = events(wire)

        // Then
        assertEquals(8, events.size)
        listOf(HASHED_KEY to 5, TARGETING_KEY to 1).forEach { (target, repetitions) ->
            val group = events.filter { it.getString("targeting_key") == target }
            assertEquals(4, group.size)
            group.forEach { event ->
                assertEquals(
                    repetitions * if (event.getJSONObject("flag").getString("key") == "enabled") 2 else 1,
                    event.getInt("evaluation_count")
                )
                assertFalse(event.getJSONObject("context").has("evaluation"))
            }
            assertEquals(
                setOf("FLAG_NOT_FOUND", "TYPE_MISMATCH", "PARSE_ERROR"),
                group.filter { it.has("error") }.map { it.getJSONObject("error").getString("message") }.toSet()
            )
        }
        assertFalse(wire.contains(PRIVATE_CONTEXT))
        assertFalse(wire.contains(PRIVATE_ERROR))
        assertEquals(0, exposureCount.get())
        val subject = JSONObject(checkNotNull(assignmentRequests.poll()))
            .getJSONObject("data").getJSONObject("attributes").getJSONObject("subject")
        assertEquals(TARGETING_KEY, subject.getString("targeting_key"))
    }

    @Test
    fun mustRetryTheSameProtectedPayload_whenPeriodicUploadFails() {
        // Given
        startSdk(flushIntervalMs = 1000)
        refresh()
        retryUploads.set(1)

        // When
        assertTrue(booleanValue("enabled"))
        val first = takeUpload()
        val retry = takeUpload()

        // Then
        assertEquals(first, retry)
        assertEquals(HASHED_KEY, events(first).single().getString("targeting_key"))
        assertFalse(first.contains(TARGETING_KEY))
        assertFalse(first.contains(PRIVATE_CONTEXT))
        assertEquals(1, events(first).single().getInt("evaluation_count"))
    }

    @Test
    fun mustPreserveEmptyAndOmitMalformedKeys_whenEvaluating() {
        // Given
        startSdk()

        // When
        listOf(false, true).forEach { consent ->
            assignmentBody.set(assignments(consent))
            listOf("", "\uD800", "\uDC00", UNICODE_KEY).forEach { key ->
                refresh(key)
                assertTrue(booleanValue("enabled"))
            }
        }
        flushOnStop()
        val wire = takeUpload()
        val events = events(wire)

        // Then
        assertEquals(6, events.size)
        assertEquals(8, events.sumOf { it.getInt("evaluation_count") })
        val emptyKeys = events.filter { it.opt("targeting_key") == "" }
        assertEquals(2, emptyKeys.size)
        assertTrue(emptyKeys.all { it.getInt("evaluation_count") == 1 })
        val omittedKeys = events.filter { !it.has("targeting_key") }
        assertEquals(2, omittedKeys.size)
        assertTrue(omittedKeys.all { it.getInt("evaluation_count") == 2 })
        assertEquals(
            setOf(UNICODE_KEY, HASHED_UNICODE_KEY),
            events.filter { it.has("targeting_key") && it.getString("targeting_key").isNotEmpty() }
                .map { it.getString("targeting_key") }.toSet()
        )
        assertTrue(events.all { !it.getJSONObject("context").has("evaluation") })
        assertFalse(wire.contains(PRIVATE_CONTEXT))
        assertEquals(0, exposureCount.get())
    }

    @Test
    fun mustEmitNothing_whenTrackingIsDisabled() {
        // Given
        startSdk(trackEvaluations = false)
        assignmentBody.set(assignments(true))
        refresh()

        // When
        assertTrue(booleanValue("enabled"))
        assertFalse(booleanValue("missing"))
        flushOnStop()

        // Then
        assertNull(sdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME))
        assertTrue(uploads.isEmpty())
        assertEquals(0, exposureCount.get())
    }

    @Test
    fun mustRestoreProtectedConsent_whenRestartAndRefreshFails() {
        assertRestoredConsent(false)
    }

    @Test
    fun mustRestoreFullConsent_whenRestartAndRefreshFails() {
        assertRestoredConsent(true)
    }

    @Test
    fun mustOmitMalformedProtectedCachedKey_whenRestartAndRefreshFails() {
        assertRestoredConsent(false, "\uD800", null)
    }

    @Test
    fun mustOmitMalformedFullCachedKey_whenRestartAndRefreshFails() {
        assertRestoredConsent(true, "\uDC00", null)
    }

    private fun assertRestoredConsent(
        consent: Boolean,
        key: String = TARGETING_KEY,
        expectedKey: String? = if (consent) key else HASHED_KEY
    ) {
        // Given
        startSdk()
        assignmentBody.set(assignments(consent))
        refresh(key)
        runBlocking { OpenFeatureAPI.shutdown() }
        Datadog._internalProxy().flushAndShutdownExecutors()
        Datadog.stopInstance()
        assignmentStatus.set(503)

        // When
        startSdk()
        val failure = updateNativeContext(key)
        if (useOpenFeature) {
            val provider = client.asOpenFeatureProvider()
            assertTrue(provider.getBooleanEvaluation("enabled", false, null).value)
            assertFalse(provider.getBooleanEvaluation("missing", false, null).value)
        } else {
            assertTrue(booleanValue("enabled"))
            assertFalse(booleanValue("missing"))
        }
        flushOnStop()
        val wire = takeUpload()

        // Then
        assertTrue("Expected the offline refresh to fail", failure != null)
        assertEquals(2, events(wire).size)
        events(wire).forEach {
            if (expectedKey == null) {
                assertFalse(it.has("targeting_key"))
            } else {
                assertEquals(expectedKey, it.getString("targeting_key"))
            }
            assertEquals(1, it.getInt("evaluation_count"))
        }
        assertFalse(wire.contains(PRIVATE_CONTEXT))
    }

    private fun startSdk(trackEvaluations: Boolean = true, flushIntervalMs: Long = 60_000) {
        val config = Configuration.Builder("pub00000000000000000000000000000000", "instrumented", "privacy")
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .apply { _InternalProxy.allowClearTextHttp(this) }
            .build()
        sdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            config,
            TrackingConsent.GRANTED
        ) as FeatureSdkCore
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint(server.url("/assignments").toString())
                .useCustomEvaluationEndpoint(server.url("/evaluations").toString())
                .useCustomExposureEndpoint(server.url("/exposures").toString())
                .evaluationFlushInterval(flushIntervalMs)
                .initializationTimeout(WAIT_MS)
                .trackEvaluations(trackEvaluations)
                .build(),
            sdkCore
        )
        client = FlagsClient.Builder(clientName, sdkCore).build()
    }

    private fun refresh(key: String = TARGETING_KEY) {
        if (useOpenFeature) {
            runBlocking {
                withTimeout(WAIT_MS) {
                    OpenFeatureAPI.setProviderAndWait(
                        client.asOpenFeatureProvider(),
                        ImmutableContext(key, mapOf("email" to Value.String(PRIVATE_CONTEXT)))
                    )
                }
            }
        } else {
            assertNull("Assignment fetch failed", updateNativeContext(key))
        }
    }

    private fun updateNativeContext(key: String): Throwable? {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        client.setEvaluationContext(
            EvaluationContext(key, mapOf("email" to PRIVATE_CONTEXT)),
            object : EvaluationContextCallback {
                override fun onSuccess() = completed.countDown()
                override fun onFailure(error: Throwable) {
                    failure.set(error)
                    completed.countDown()
                }
            }
        )
        assertTrue("Assignment callback timed out", completed.await(WAIT_MS, TimeUnit.MILLISECONDS))
        return failure.get()
    }

    private fun booleanValue(key: String): Boolean = if (useOpenFeature) {
        OpenFeatureAPI.getClient().getBooleanValue(key, false)
    } else {
        client.resolveBooleanValue(key, false)
    }

    private fun integerValue(key: String): Int = if (useOpenFeature) {
        OpenFeatureAPI.getClient().getIntegerValue(key, 0)
    } else {
        client.resolveIntValue(key, 0)
    }

    private fun flushOnStop() {
        sdkCore.getFeature(Feature.FLAGS_EVALUATIONS_FEATURE_NAME)?.unwrap<Feature>()?.onStop()
        Datadog._internalProxy().flushAndShutdownExecutors()
    }

    private fun takeUpload(): String = checkNotNull(uploads.poll(WAIT_MS, TimeUnit.MILLISECONDS)) {
        "No evaluation HTTP upload arrived on the Android runtime"
    }

    private fun events(wire: String): List<JSONObject> =
        wire.lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()

    private fun body(request: RecordedRequest): String = if (request.getHeader("Content-Encoding") == "gzip") {
        GzipSource(request.body).buffer().use { it.readUtf8() }
    } else {
        request.body.readUtf8()
    }

    companion object {
        private const val WAIT_MS = 60_000L
        private const val UNICODE_KEY = "café-\uD83D\uDE00"
        private const val HASHED_UNICODE_KEY = "sha256_8b6ed8e437d3dd3fe8b31c664aaa82cb983764a56c2f81cb7d5319d011e9895d"
        private const val TARGETING_KEY = "jane.doe@datadoghq.com"
        private const val HASHED_KEY = "sha256_b4698f9b6d186781fa8dc59e533578fa2d8379a46b1cf6db85cda6aa9c99e51b"
        private const val PRIVATE_CONTEXT = "private-context-canary@example.com"
        private const val PRIVATE_ERROR = "private-error-canary@example.com"

        @JvmStatic
        @Parameterized.Parameters(name = "openFeature={0}")
        fun parameters(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))

        private fun assignments(consent: Any?): String {
            fun flag(type: String, value: Any) = JSONObject()
                .put("variationType", type).put("variationValue", value)
                .put("variationKey", "variant").put("allocationKey", "allocation")
                .put("doLog", false).put("reason", "TARGETING_MATCH").put("extraLogging", JSONObject())
            val flags = JSONObject()
                .put("enabled", flag("boolean", true))
                .put("wrong-type", flag("string", PRIVATE_ERROR))
                .put("broken", flag("integer", PRIVATE_ERROR))
            return JSONObject().put(
                "data",
                JSONObject().put("type", "precomputed-assignments").put(
                    "attributes",
                    JSONObject().put("flags", flags).put("observeFullEvaluationData", consent)
                )
            ).toString()
        }
    }
}
