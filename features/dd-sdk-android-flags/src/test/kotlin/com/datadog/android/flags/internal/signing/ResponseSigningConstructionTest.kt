/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import android.content.Context
import android.content.pm.ApplicationInfo
import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.internal.time.TimeProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

internal class ResponseSigningConstructionTest {
    private val mockLogger = mock<InternalLogger>()
    private val mockCalls = mock<Call.Factory>()
    private val mockExecutor = mock<ExecutorService> {
        on { execute(any()) } doAnswer { invocation -> invocation.getArgument<Runnable>(0).run() }
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("modesAndSites")
    fun `M enable signing only on staging W build() {explicit opt in}`(mode: ResponseSigningMode, site: DatadogSite) {
        // Given
        val (mockCore, feature) = startFeature(mode, site)
        val signed = mode != ResponseSigningMode.OFF && site == DatadogSite.STAGING
        val observeOffStaging = mode == ResponseSigningMode.OBSERVE && site != DatadogSite.STAGING
        val requiredOffStaging = mode == ResponseSigningMode.REQUIRED && site != DatadogSite.STAGING

        // When
        val result = FlagsClient.Builder(sdkCore = mockCore).build()

        // Then
        assertThat(result).isInstanceOf(DatadogFlagsClient::class.java)
        val configuration = argumentCaptor<OkHttpClient.Builder.() -> Unit>()
        verify(mockCore).createOkHttpCallFactory(configuration.capture())
        val transport = OkHttpClient.Builder().apply(configuration.lastValue).build()
        assertThat(transport.followRedirects).isEqualTo(!signed)
        assertThat(transport.followSslRedirects).isEqualTo(!signed)
        assertThat(userMessages(InternalLogger.Level.WARN).count { it == OBSERVE_MESSAGE })
            .isEqualTo(if (observeOffStaging) 1 else 0)
        assertThat(userMessages(InternalLogger.Level.ERROR).count { it == REQUIRED_MESSAGE })
            .isEqualTo(if (requiredOffStaging) 1 else 0)
        if (mode == ResponseSigningMode.OFF) {
            verify(mockCore, never()).setContextUpdateReceiver(any())
        } else {
            val receiver = argumentCaptor<FeatureContextUpdateReceiver>()
            verify(mockCore).setContextUpdateReceiver(receiver.capture())
            assertThat(receiver.lastValue).isSameAs(feature.signingContext)
            feature.onStop()
            verify(mockCore).removeContextUpdateReceiver(receiver.lastValue)
            assertThat(checkNotNull(feature.signingContext).snapshot().active).isFalse()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonStagingSites")
    fun `M fail closed without a request W setEvaluationContext() {REQUIRED off staging}`(site: DatadogSite) {
        // Given
        val (mockCore, _) = startFeature(ResponseSigningMode.REQUIRED, site)
        val client = FlagsClient.Builder(sdkCore = mockCore).build()

        // When
        val failures = List(2) { fetch(client, "user-$it") }

        // Then
        failures.forEach { failure ->
            assertThat(failure).isInstanceOf(UnsupportedOperationException::class.java)
                .hasMessage(REQUIRED_MESSAGE)
        }
        val state = client.state.getCurrentState()
        assertThat(state).isInstanceOf(FlagsClientState.Error::class.java)
        assertThat((state as FlagsClientState.Error).error).isInstanceOf(UnsupportedOperationException::class.java)
        assertThat(client.resolveBooleanValue("any-flag", true)).isTrue()
        verify(mockCalls, never()).newCall(any())
        assertThat(userMessages(InternalLogger.Level.ERROR).count { it == REQUIRED_MESSAGE }).isEqualTo(1)
    }

    @Test
    fun `M keep fetching W setEvaluationContext() {OBSERVE off staging}`() {
        // Given
        val (mockCore, _) = startFeature(ResponseSigningMode.OBSERVE, DatadogSite.US1, initializationTimeoutMs = 0)
        val client = FlagsClient.Builder(sdkCore = mockCore).build()

        // When
        val failure = fetch(client, "user")

        // Then
        assertThat(failure).isNotInstanceOf(UnsupportedOperationException::class.java)
        verify(mockCalls).newCall(any())
        assertThat(userMessages(InternalLogger.Level.ERROR)).doesNotContain(REQUIRED_MESSAGE)
        assertThat(userMessages(InternalLogger.Level.WARN).count { it == OBSERVE_MESSAGE }).isEqualTo(1)
    }

    private fun startFeature(
        mode: ResponseSigningMode,
        site: DatadogSite,
        initializationTimeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Pair<InternalSdkCore, FlagsFeature> {
        val mockTime = mock<TimeProvider>()
        val mockStore = mock<DataStoreHandler>()
        val mockDatadogContext = mock<DatadogContext> {
            on { this.site } doReturn site
            on { clientToken } doReturn "pub-test"
            on { source } doReturn "android"
            on { env } doReturn "test"
            on { sdkVersion } doReturn "0.0.0"
            on { service } doReturn "test"
            on { featuresContext } doReturn emptyMap()
        }
        val mockCore = mock<InternalSdkCore> {
            on { internalLogger } doReturn mockLogger
            on { timeProvider } doReturn mockTime
            on { createSingleThreadExecutorService(any()) } doReturn mockExecutor
            on { createOkHttpCallFactory(any()) } doReturn mockCalls
            on { getDatadogContext(any()) } doReturn mockDatadogContext
        }
        val feature = FlagsFeature(
            mockCore,
            FlagsConfiguration.Builder()
                .trackEvaluations(false)
                .initializationTimeout(initializationTimeoutMs)
                .responseSigning(mode)
                .build()
        )
        val mockScope = mock<FeatureScope> {
            on { unwrap<FlagsFeature>() } doReturn feature
            on { dataStore } doReturn mockStore
        }
        whenever(mockCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockScope
        val applicationInfo = ApplicationInfo()
        val mockContext = mock<Context> { on { this.applicationInfo } doReturn applicationInfo }
        feature.onInitialize(mockContext)
        return mockCore to feature
    }

    private fun fetch(client: FlagsClient, targetingKey: String): Throwable? {
        var outcome: Throwable? = null
        client.setEvaluationContext(
            EvaluationContext(targetingKey, emptyMap()),
            object : EvaluationContextCallback {
                override fun onSuccess() = Unit
                override fun onFailure(error: Throwable) {
                    outcome = error
                }
            }
        )
        return outcome
    }

    private fun userMessages(level: InternalLogger.Level): List<String> {
        val messages = argumentCaptor<() -> String>()
        verify(mockLogger, atLeast(0)).log(
            eq(level),
            eq(InternalLogger.Target.USER),
            messages.capture(),
            anyOrNull(),
            any(),
            anyOrNull()
        )
        return messages.allValues.map { it() }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 5_000L
        private const val OBSERVE_MESSAGE = FlagsClient.RESPONSE_SIGNING_OBSERVE_UNAVAILABLE_MESSAGE
        private const val REQUIRED_MESSAGE = FlagsClient.RESPONSE_SIGNING_REQUIRED_UNAVAILABLE_MESSAGE

        @JvmStatic
        fun modesAndSites(): List<Arguments> = ResponseSigningMode.values().flatMap { mode ->
            DatadogSite.values().map { site -> Arguments.of(mode, site) }
        }

        @JvmStatic
        fun nonStagingSites(): List<Arguments> = DatadogSite.values()
            .filter { it != DatadogSite.STAGING }
            .map { Arguments.of(it) }
    }
}
