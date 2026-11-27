/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.evaluation.AssignmentAuthorization
import com.datadog.android.flags.internal.evaluation.ExperimentationOptOut
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Trigger
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import com.datadog.android.internal.time.TimeProvider
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrecomputedConfigurationFetcherTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockFlagsFeatureScope: FeatureScope

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockAssignmentsReader: PrecomputedAssignmentsReader

    @Mock
    lateinit var mockPrecomputeMapper: PrecomputeMapper

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeAssignments: PrecomputedAssignments

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeBody: String

    @LongForgery(min = 1L)
    var fakeNow: Long = 0L

    private lateinit var testedFetcher: PrecomputedConfigurationFetcher

    @BeforeEach
    fun `set up`() {
        fakeDatadogContext = fakeDatadogContext.copy(source = "android")
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeNow
        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockFlagsFeatureScope
        whenever(mockSdkCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doReturn fakeDatadogContext
        whenever(mockPrecomputeMapper.map(eq(fakeBody), anyOrNull(), any())) doReturn fakeAssignments

        testedFetcher = createFetcher()
    }

    // region success

    @Test
    fun `M return detached configuration W fetch() { successful response }`(
        @StringForgery fakeEtag: String,
        @StringForgery fakeIgnoredReason: String
    ) {
        // Given
        val context = EvaluationContext(fakeTargetingKey, mapOf("plan" to "premium"))
        whenever(mockAssignmentsReader.readPrecomputedFlags(eq(context), eq(fakeDatadogContext), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, fakeIgnoredReason, fakeEtag)

        // When
        val result = testedFetcher.fetch(context)

        // Then
        assertThat(result).isEqualTo(
            PrecomputedConfigurationFetchResult.Success(
                configuration = FetchedPrecomputedConfiguration(
                    response = fakeBody,
                    context = context,
                    fetchedAt = fakeNow,
                    etag = fakeEtag,
                    assignments = fakeAssignments
                ),
                flagSelectionIgnoredReason = fakeIgnoredReason
            )
        )
    }

    @Test
    fun `M keep a null etag W fetch() { response without ETag }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey)
        whenever(mockAssignmentsReader.readPrecomputedFlags(eq(context), eq(fakeDatadogContext), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, null)

        // When
        val result = testedFetcher.fetch(context) as PrecomputedConfigurationFetchResult.Success

        // Then
        assertThat(result.configuration.etag).isNull()
        assertThat(result.flagSelectionIgnoredReason).isNull()
    }

    @Test
    fun `M send initialization then context change diagnostics W fetch() { two fetches }`() {
        // Given
        val context = EvaluationContext(fakeTargetingKey)
        whenever(mockAssignmentsReader.readPrecomputedFlags(any(), any(), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, null)

        // When
        testedFetcher.fetch(context)
        testedFetcher.fetch(context)

        // Then
        argumentCaptor<Diagnostics> {
            verify(mockAssignmentsReader, times(2))
                .readPrecomputedFlags(eq(context), eq(fakeDatadogContext), capture())
            assertThat(firstValue.fetch.sequence).isEqualTo(1L)
            assertThat(firstValue.fetch.trigger).isEqualTo(Trigger.INITIALIZATION)
            assertThat(secondValue.fetch.sequence).isEqualTo(2L)
            assertThat(secondValue.fetch.trigger).isEqualTo(Trigger.CONTEXT_CHANGE)
            assertThat(secondValue.sdk.instanceId).isEqualTo(firstValue.sdk.instanceId)
        }
    }

    @Test
    fun `M keep the token out of the configuration W fetch() { assignment token }`(
        @StringForgery(regex = "[a-zA-Z0-9]{16,32}") fakeToken: String
    ) {
        // Given
        val context = EvaluationContext(
            fakeTargetingKey,
            mapOf("plan" to "premium", AssignmentAuthorization.ATTRIBUTE to fakeToken)
        )
        val targetingContext = EvaluationContext(fakeTargetingKey, mapOf("plan" to "premium"))
        whenever(
            mockAssignmentsReader.readPrecomputedFlags(
                eq(targetingContext),
                eq(fakeDatadogContext),
                any(),
                eq(fakeToken)
            )
        ) doReturn PrecomputedAssignmentsResponse(fakeBody, null)

        // When
        val result = testedFetcher.fetch(context) as PrecomputedConfigurationFetchResult.Success

        // Then
        assertThat(result.configuration.context).isEqualTo(targetingContext)
        assertThat(result.configuration.context.attributes).doesNotContainKey(AssignmentAuthorization.ATTRIBUTE)
        verify(mockPrecomputeMapper).map(eq(fakeBody), isNull(), eq(true))
    }

    @Test
    fun `M acknowledge the opt-out W fetch() { valid opt-out attribute }`() {
        // Given
        testedFetcher = createFetcher(ExperimentationOptOutMode.IMMEDIATE)
        val context = EvaluationContext(
            fakeTargetingKey,
            mapOf(ExperimentationOptOut.ATTRIBUTE to "2026-01-01T00:00:00Z")
        )
        whenever(mockAssignmentsReader.readPrecomputedFlags(eq(context), eq(fakeDatadogContext), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, null)

        // When
        val result = testedFetcher.fetch(context)

        // Then
        assertThat(result).isInstanceOf(PrecomputedConfigurationFetchResult.Success::class.java)
        argumentCaptor<ExperimentationOptOut.Request> {
            verify(mockPrecomputeMapper).map(eq(fakeBody), capture(), eq(false))
            assertThat(firstValue.optedOut).isTrue()
            assertThat(firstValue.mode).isEqualTo(ExperimentationOptOutMode.IMMEDIATE)
        }
    }

    @Test
    fun `M accept obfuscated assignments W fetch() { native Android source }`() {
        // Given
        val obfuscated = fakeAssignments.copy(obfuscation = FlagKeyObfuscation.fromSalt(FAKE_SALT))
        whenever(mockPrecomputeMapper.map(eq(fakeBody), anyOrNull(), any())) doReturn obfuscated
        whenever(mockAssignmentsReader.readPrecomputedFlags(any(), any(), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, null)

        // When
        val result = testedFetcher.fetch(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat((result as PrecomputedConfigurationFetchResult.Success).configuration.assignments)
            .isEqualTo(obfuscated)
    }

    // endregion

    // region failures

    @Test
    fun `M return INVALID_CONTEXT without a request W fetch() { malformed opt-out attribute }`() {
        // Given
        testedFetcher = createFetcher(ExperimentationOptOutMode.IMMEDIATE)
        val context = EvaluationContext(fakeTargetingKey, mapOf(ExperimentationOptOut.ATTRIBUTE to "not-a-timestamp"))

        // When
        val result = testedFetcher.fetch(context)

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.INVALID_CONTEXT))
        verifyNoInteractions(mockAssignmentsReader)
    }

    @Test
    fun `M return UNAVAILABLE without a request W fetch() { Flags feature not registered }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn null

        // When
        val result = testedFetcher.fetch(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.UNAVAILABLE))
        verifyNoInteractions(mockAssignmentsReader)
    }

    @Test
    fun `M return UNAVAILABLE without a request W fetch() { no SDK context }`() {
        // Given
        whenever(mockSdkCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doReturn null

        // When
        val result = testedFetcher.fetch(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.UNAVAILABLE))
        verifyNoInteractions(mockAssignmentsReader)
    }

    @Test
    fun `M return NETWORK_ERROR W fetch() { request fails }`() {
        // Given
        whenever(mockAssignmentsReader.readPrecomputedFlags(any(), any(), any())) doReturn null

        // When
        val result = testedFetcher.fetch(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.NETWORK_ERROR))
        verify(mockPrecomputeMapper, never()).map(any(), anyOrNull(), any())
    }

    @Test
    fun `M return AUTHORIZATION_REJECTED W fetch() { server rejects the token }`(
        @StringForgery(regex = "[a-zA-Z0-9]{16,32}") fakeToken: String
    ) {
        // Given
        val context = EvaluationContext(fakeTargetingKey, mapOf(AssignmentAuthorization.ATTRIBUTE to fakeToken))
        whenever(mockAssignmentsReader.readPrecomputedFlags(any(), any(), any(), eq(fakeToken))) doReturn
            PrecomputedAssignmentsResult.AuthorizationRejected

        // When
        val result = testedFetcher.fetch(context)

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.AUTHORIZATION_REJECTED))
    }

    @Test
    fun `M return INVALID_RESPONSE W fetch() { body cannot be parsed }`() {
        // Given
        whenever(mockAssignmentsReader.readPrecomputedFlags(any(), any(), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, null)
        whenever(mockPrecomputeMapper.map(eq(fakeBody), anyOrNull(), any())) doReturn null

        // When
        val result = testedFetcher.fetch(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.INVALID_RESPONSE))
    }

    @Test
    fun `M return INVALID_RESPONSE W fetch() { obfuscated assignments for a cross-platform source }`(
        @StringForgery(regex = "react-native|flutter") fakeSource: String
    ) {
        // Given
        whenever(mockSdkCore.getDatadogContext(setOf(Feature.RUM_FEATURE_NAME))) doReturn
            fakeDatadogContext.copy(source = fakeSource)
        whenever(mockAssignmentsReader.readPrecomputedFlags(any(), any(), any())) doReturn
            PrecomputedAssignmentsResponse(fakeBody, null)
        whenever(mockPrecomputeMapper.map(eq(fakeBody), anyOrNull(), any())) doReturn
            fakeAssignments.copy(obfuscation = FlagKeyObfuscation.fromSalt(FAKE_SALT))

        // When
        val result = testedFetcher.fetch(EvaluationContext(fakeTargetingKey))

        // Then
        assertThat(result).isEqualTo(failure(PrecomputedConfigurationFetchResult.Reason.INVALID_RESPONSE))
    }

    // endregion

    private fun createFetcher(mode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED) =
        PrecomputedConfigurationFetcher(
            sdkCore = mockSdkCore,
            assignmentsReader = mockAssignmentsReader,
            precomputeMapper = mockPrecomputeMapper,
            experimentationOptOutMode = mode
        )

    private fun failure(reason: PrecomputedConfigurationFetchResult.Reason) =
        PrecomputedConfigurationFetchResult.Failure(reason)

    private companion object {
        const val FAKE_SALT = "000102030405060708090a0b0c0d0e0f"
    }
}
