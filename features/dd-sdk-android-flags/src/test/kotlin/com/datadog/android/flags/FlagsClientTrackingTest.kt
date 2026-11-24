/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.EvaluationsFeature
import com.datadog.android.flags.internal.ExposureEventsProcessor
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.internal.flags.RumFlagEvaluationMessage
import com.datadog.android.internal.time.TimeProvider
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

/**
 * Covers the per-client [FlagsClient.Builder.trackExposures] and [FlagsClient.Builder.trackEvaluations] options with
 * clients that share the feature's exposure processor, as clients of one SDK instance do.
 */
@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsClientTrackingTest {

    @Mock
    lateinit var mockCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockFlagsScope: FeatureScope

    @Mock
    lateinit var mockRumScope: FeatureScope

    @Mock
    lateinit var mockExposureWriter: RecordWriter

    @Mock
    lateinit var mockEvaluationsFeature: EvaluationsFeature

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @StringForgery
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    private val readCallbacks = mutableListOf<DataStoreReadCallback<FlagsStateEntry>>()

    @BeforeEach
    fun `set up`() {
        whenever(mockCore.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockCore.timeProvider).thenReturn(mockTimeProvider)
        whenever(mockCore.createSingleThreadExecutorService(any())).thenReturn(mock<ExecutorService>())
        whenever(mockCore.createOkHttpCallFactory(any())).thenReturn(mock<Call.Factory>())
        whenever(mockFlagsScope.dataStore).thenReturn(mockDataStore)
        whenever(mockCore.getFeature(FLAGS_FEATURE_NAME)).thenReturn(mockFlagsScope)
        whenever(mockCore.getFeature(RUM_FEATURE_NAME)).thenReturn(mockRumScope)
        doAnswer {
            readCallbacks.add(it.getArgument(2))
            null
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
    }

    @Test
    fun `M track exposures and evaluations W resolveBooleanValue() {default client options}`() {
        // Given
        val testedClient = newClient("default")

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, false)

        // Then
        assertThat(result).isTrue()
        verifyExposuresWritten(1)
        verifyEvaluationsTracked(1)
        verifyRumEvaluationsLogged(1)
    }

    @Test
    fun `M track evaluations only W resolveBooleanValue() {trackExposures false}`() {
        // Given
        val testedClient = newClient("peek", trackExposures = false)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, false)

        // Then
        assertThat(result).isTrue()
        verifyExposuresWritten(0)
        verifyEvaluationsTracked(1)
        verifyRumEvaluationsLogged(1)
    }

    @Test
    fun `M track exposures only W resolveBooleanValue() {trackEvaluations false}`() {
        // Given
        val testedClient = newClient("peek", trackEvaluations = false)

        // When
        val result = testedClient.resolveBooleanValue(fakeFlagKey, false)

        // Then
        assertThat(result).isTrue()
        verifyExposuresWritten(1)
        verifyEvaluationsTracked(0)
        verifyRumEvaluationsLogged(0)
    }

    @Test
    fun `M track nothing and resolve the same value W resolve() {trackExposures and trackEvaluations false}`() {
        // Given
        val defaultClient = newClient("default")
        val testedClient = newClient("peek", trackExposures = false, trackEvaluations = false)

        // When
        val result = testedClient.resolve(fakeFlagKey, false)

        // Then
        assertThat(result).isEqualTo(defaultClient.resolve(fakeFlagKey, false))
        verifyExposuresWritten(1)
        verifyEvaluationsTracked(1)
        verifyRumEvaluationsLogged(1)
    }

    @Test
    fun `M send the exposure through the default client W resolveBooleanValue() {peeking client evaluated first}`() {
        // Given
        val peekingClient = newClient("peek", trackExposures = false, trackEvaluations = false)
        val defaultClient = newClient("default")
        peekingClient.resolveBooleanValue(fakeFlagKey, false)
        verifyExposuresWritten(0)

        // When
        defaultClient.resolveBooleanValue(fakeFlagKey, false)
        defaultClient.resolveBooleanValue(fakeFlagKey, false)

        // Then
        val exposure = argumentCaptor<ExposureEvent> {
            verify(mockExposureWriter).write(capture())
        }.firstValue
        assertThat(exposure.flag.key).isEqualTo(fakeFlagKey)
        assertThat(exposure.subject.id).isEqualTo(fakeTargetingKey)
    }

    @Test
    fun `M not defer an exposure W resolveDeferringExposure() {trackExposures false}`() {
        // Given
        val testedClient = newClient("peek", trackExposures = false)

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.details.value).isTrue()
        assertThat(result.recordExposure as Any?).isNull()
        verifyExposuresWritten(0)
    }

    @Test
    fun `M defer an exposure W resolveDeferringExposure() {default client options}`() {
        // Given
        val testedClient = newClient("default")

        // When
        val result = testedClient.resolveDeferringExposure(fakeFlagKey, false)

        // Then
        assertThat(result.recordExposure?.invoke()).isFalse()
        verifyExposuresWritten(1)
    }

    @Test
    fun `M not record the exposure W trackFlagExposure() {trackExposures false}`() {
        // Given
        val peekingClient = newClient("peek", trackExposures = false)
        val defaultClient = newClient("default")

        // When
        peekingClient.trackFlagExposure(fakeFlagKey, fakeFlag, EvaluationContext(fakeTargetingKey))
        defaultClient.trackFlagExposure(fakeFlagKey, fakeFlag, EvaluationContext(fakeTargetingKey))

        // Then
        verifyExposuresWritten(1)
    }

    @Test
    fun `M not track evaluations W trackFlagEvaluationEvent() + trackFlagRumEvaluation() {trackEvaluations false}`() {
        // Given
        val testedClient = newClient("peek", trackEvaluations = false)
        val context = EvaluationContext(fakeTargetingKey)

        // When
        testedClient.trackFlagEvaluationEvent(fakeFlagKey, fakeFlag, context, null)
        testedClient.trackFlagRumEvaluation(fakeFlagKey, fakeFlag.variationKey, context)

        // Then
        verifyEvaluationsTracked(0)
        verifyRumEvaluationsLogged(0)
    }

    @Test
    fun `M not enable tracking W resolveBooleanValue() {client options on, feature configuration off}`() {
        // Given
        val configuration = FlagsConfiguration.Builder()
            .trackExposures(false)
            .trackEvaluations(false)
            .rumIntegrationEnabled(false)
            .build()
        val testedClient = newClient(
            "default",
            featureConfiguration = configuration,
            trackExposures = true,
            trackEvaluations = true
        )

        // When
        testedClient.resolveBooleanValue(fakeFlagKey, false)

        // Then
        verifyExposuresWritten(0)
        verifyEvaluationsTracked(0)
        verifyRumEvaluationsLogged(0)
    }

    // region Helpers

    private val fakeFlag: PrecomputedFlag
        get() = PrecomputedFlag("boolean", "true", true, "allocation", "on", JSONObject(), "TARGETING_MATCH")

    private val sharedProcessors = mutableMapOf<FlagsConfiguration, FlagsFeature>()

    private fun newClient(
        name: String,
        featureConfiguration: FlagsConfiguration = DEFAULT_CONFIGURATION,
        trackExposures: Boolean = true,
        trackEvaluations: Boolean = true
    ): DatadogFlagsClient {
        // Clients of one SDK instance share the feature, so they share its exposure processor.
        val flagsFeature = sharedProcessors.getOrPut(featureConfiguration) {
            FlagsFeature(mockCore, featureConfiguration).apply {
                processor = ExposureEventsProcessor(
                    writer = mockExposureWriter,
                    timeProvider = mockTimeProvider,
                    writeEvents = featureConfiguration.trackExposures
                )
            }
        }
        val client = FlagsClient.createInternal(
            configuration = featureConfiguration,
            featureSdkCore = mockCore,
            flagsFeature = flagsFeature,
            evaluationsFeature = mockEvaluationsFeature,
            name = name,
            evaluationTelemetryOptOutParameterEnabled = false,
            trackExposures = trackExposures,
            trackEvaluations = trackEvaluations
        ) as DatadogFlagsClient
        readCallbacks.last().onSuccess(
            DataStoreContent(
                1,
                FlagsStateEntry(
                    evaluationContext = EvaluationContext(fakeTargetingKey),
                    flags = mapOf(fakeFlagKey to fakeFlag),
                    lastUpdateTimestamp = 0L
                )
            )
        )
        return client
    }

    private fun verifyExposuresWritten(count: Int) {
        verify(mockExposureWriter, times(count)).write(any())
    }

    private fun verifyEvaluationsTracked(count: Int) {
        verify(mockEvaluationsFeature, times(count)).processEvaluation(
            eq(fakeFlagKey),
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any()
        )
    }

    private fun verifyRumEvaluationsLogged(count: Int) {
        if (count == 0) {
            verify(mockRumScope, never()).sendEvent(any())
        } else {
            argumentCaptor<Any> {
                verify(mockRumScope, times(count)).sendEvent(capture())
                assertThat(allValues).allSatisfy {
                    assertThat((it as RumFlagEvaluationMessage).flagKey).isEqualTo(fakeFlagKey)
                }
            }
        }
    }

    // endregion

    private companion object {
        val DEFAULT_CONFIGURATION = FlagsConfiguration.Builder().build()
    }
}
