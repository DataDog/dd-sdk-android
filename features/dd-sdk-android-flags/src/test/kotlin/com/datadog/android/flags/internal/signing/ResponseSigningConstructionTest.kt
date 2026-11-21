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
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.DatadogFlagsClient
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.internal.time.TimeProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

internal class ResponseSigningConstructionTest {
    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("modesAndSites")
    fun `M enable signing only on staging W build() {explicit opt in}`(mode: ResponseSigningMode, site: DatadogSite) {
        // Given
        val mockLogger = mock<InternalLogger>()
        val mockTime = mock<TimeProvider>()
        val mockExecutor = mock<ExecutorService>()
        val mockCalls = mock<Call.Factory>()
        val mockStore = mock<DataStoreHandler>()
        val mockDatadogContext = mock<DatadogContext> { on { this.site } doReturn site }
        val mockCore = mock<InternalSdkCore> {
            on { internalLogger } doReturn mockLogger
            on { timeProvider } doReturn mockTime
            on { createSingleThreadExecutorService(any()) } doReturn mockExecutor
            on { createOkHttpCallFactory(any()) } doReturn mockCalls
            on { getDatadogContext(any()) } doReturn mockDatadogContext
        }
        val feature = FlagsFeature(
            mockCore,
            FlagsConfiguration.Builder().trackEvaluations(false).responseSigning(mode).build()
        )
        val mockScope = mock<FeatureScope> {
            on { unwrap<FlagsFeature>() } doReturn feature
            on { dataStore } doReturn mockStore
        }
        whenever(mockCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockScope
        val applicationInfo = ApplicationInfo()
        val mockContext = mock<Context> { on { this.applicationInfo } doReturn applicationInfo }
        feature.onInitialize(mockContext)
        val signed = mode != ResponseSigningMode.OFF && site == DatadogSite.STAGING

        // When
        val result = FlagsClient.Builder(sdkCore = mockCore).build()

        // Then
        assertThat(result).isInstanceOf(DatadogFlagsClient::class.java)
        val configuration = argumentCaptor<OkHttpClient.Builder.() -> Unit>()
        verify(mockCore).createOkHttpCallFactory(configuration.capture())
        val transport = OkHttpClient.Builder().apply(configuration.lastValue).build()
        assertThat(transport.followRedirects).isEqualTo(!signed)
        assertThat(transport.followSslRedirects).isEqualTo(!signed)
        val messages = argumentCaptor<() -> String>()
        verify(mockLogger, atLeast(0)).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            messages.capture(),
            anyOrNull(),
            any(),
            anyOrNull()
        )
        assertThat(messages.allValues.map { it() }.contains(FlagsClient.RESPONSE_SIGNING_UNAVAILABLE_MESSAGE))
            .isEqualTo(mode != ResponseSigningMode.OFF && !signed)
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

    companion object {
        @JvmStatic
        fun modesAndSites(): List<Arguments> = ResponseSigningMode.values().flatMap { mode ->
            DatadogSite.values().map { site -> Arguments.of(mode, site) }
        }
    }
}
