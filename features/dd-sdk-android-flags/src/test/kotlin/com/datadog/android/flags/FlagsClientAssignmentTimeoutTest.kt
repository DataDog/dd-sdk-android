/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.flags.internal.FlagsFeature
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Call
import okhttp3.Request
import okio.Timeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
internal class FlagsClientAssignmentTimeoutTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockDataStore: DataStoreHandler

    @Test
    fun `M apply timeout to custom assignment transport W setEvaluationContext`() {
        // Given
        val callFactory = mock<Call.Factory>()
        val call = mock<Call>()
        val timeout = mock<Timeout>()
        val configuration = FlagsConfiguration.Builder()
            .trackEvaluations(false)
            .assignmentRequestCallFactory(callFactory)
            .assignmentRequestTimeout(REQUEST_TIMEOUT_MS)
            .build()
        whenever(callFactory.newCall(any())).thenReturn(call)
        whenever(call.timeout()).thenReturn(timeout)
        whenever(timeout.timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(timeout)
        whenever(call.execute()).thenThrow(IOException("network failure"))
        whenever(mockDatadogContext.site).thenReturn(DatadogSite.US1)
        whenever(mockDatadogContext.clientToken).thenReturn(CLIENT_TOKEN)
        whenever(mockDatadogContext.env).thenReturn(ENVIRONMENT)
        whenever(mockDatadogContext.sdkVersion).thenReturn(SDK_VERSION)
        whenever(mockDatadogContext.featuresContext).thenReturn(emptyMap())

        // When
        val client = buildClient(configuration)
        client.setEvaluationContext(EvaluationContext("target"))

        // Then
        val requestCaptor = argumentCaptor<Request>()
        verify(callFactory).newCall(requestCaptor.capture())
        assertThat(requestCaptor.firstValue.method).isEqualTo("POST")
        assertThat(requestCaptor.firstValue.header("dd-client-token")).isEqualTo(CLIENT_TOKEN)
        assertThat(requestCaptor.firstValue.header("Content-Type")).isEqualTo("application/vnd.api+json")
        verify(timeout).timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        verify(call).execute()
        verify(mockSdkCore, never()).createOkHttpCallFactory()
    }

    @Test
    fun `M preserve custom assignment transport timeout W assignment timeout disabled`() {
        // Given
        val callFactory = mock<Call.Factory>()
        val call = mock<Call>()
        val configuration = FlagsConfiguration.Builder()
            .trackEvaluations(false)
            .assignmentRequestCallFactory(callFactory)
            .build()
        whenever(callFactory.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenThrow(IOException("network failure"))
        whenever(mockDatadogContext.site).thenReturn(DatadogSite.US1)
        whenever(mockDatadogContext.clientToken).thenReturn(CLIENT_TOKEN)
        whenever(mockDatadogContext.env).thenReturn(ENVIRONMENT)
        whenever(mockDatadogContext.sdkVersion).thenReturn(SDK_VERSION)
        whenever(mockDatadogContext.featuresContext).thenReturn(emptyMap())

        // When
        val client = buildClient(configuration)
        client.setEvaluationContext(EvaluationContext("target"))

        // Then
        verify(callFactory, times(1)).newCall(any())
        verify(call, times(1)).execute()
        verify(call, never()).timeout()
        verify(mockSdkCore, never()).createOkHttpCallFactory()
    }

    private fun buildClient(configuration: FlagsConfiguration): FlagsClient {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        val flagsFeature = FlagsFeature(mockSdkCore, configuration)
        whenever(mockSdkCore.createSingleThreadExecutorService(any())).thenReturn(mockExecutorService)
        whenever(mockSdkCore.getFeature(FLAGS_FEATURE_NAME)).thenReturn(mockFeatureScope)
        whenever(mockFeatureScope.dataStore).thenReturn(mockDataStore)
        whenever(mockFeatureScope.unwrap<FlagsFeature>()).thenReturn(flagsFeature)
        doAnswer { invocation -> invocation.getArgument<Runnable>(0).run() }
            .whenever(mockExecutorService).execute(any())
        doAnswer { invocation ->
            invocation.getArgument<(DatadogContext) -> Unit>(1).invoke(mockDatadogContext)
        }.whenever(mockFeatureScope).withContext(any(), any())
        return FlagsClient.Builder(sdkCore = mockSdkCore).build()
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 1_500L
        const val CLIENT_TOKEN = "client-token"
        const val ENVIRONMENT = "test"
        const val SDK_VERSION = "1.0.0"
    }
}
