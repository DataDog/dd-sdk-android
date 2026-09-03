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
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
internal class FlagsClientAssignmentTransportTest {

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
    fun `M apply assignment transport policies W FlagsClient Builder`() {
        // Given
        val callFactory = mock<Call.Factory>()
        val firstCall = mock<Call>()
        val secondCall = mock<Call>()
        val firstTimeout = mock<Timeout>()
        val secondTimeout = mock<Timeout>()
        val configuration = FlagsConfiguration.Builder()
            .trackEvaluations(false)
            .assignmentRequestCallFactory(callFactory)
            .assignmentRequestTimeout(REQUEST_TIMEOUT_MS)
            .assignmentRequestRetryCount(1)
            .build()
        whenever(callFactory.newCall(any())).thenReturn(firstCall, secondCall)
        whenever(firstCall.timeout()).thenReturn(firstTimeout)
        whenever(secondCall.timeout()).thenReturn(secondTimeout)
        whenever(firstTimeout.timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(firstTimeout)
        whenever(secondTimeout.timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(secondTimeout)
        whenever(firstCall.execute()).thenThrow(SocketTimeoutException("first attempt"))
        whenever(secondCall.execute()).thenThrow(SocketTimeoutException("second attempt"))
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
        verify(callFactory, times(2)).newCall(requestCaptor.capture())
        requestCaptor.allValues.forEach { request ->
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.header("dd-client-token")).isEqualTo(CLIENT_TOKEN)
            assertThat(request.header("Content-Type")).isEqualTo("application/vnd.api+json")
        }
        verify(firstTimeout).timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        verify(secondTimeout).timeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        verify(firstCall, times(1)).execute()
        verify(secondCall, times(1)).execute()
        verify(mockSdkCore, never()).createOkHttpCallFactory()
    }

    @Test
    fun `M make one assignment request W FlagsClient Builder { retry not configured }`() {
        // Given
        val callFactory = mock<Call.Factory>()
        val call = mock<Call>()
        val configuration = FlagsConfiguration.Builder()
            .trackEvaluations(false)
            .assignmentRequestCallFactory(callFactory)
            .build()
        whenever(callFactory.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenThrow(IOException("retryable failure"))
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
    }

    private fun buildClient(configuration: FlagsConfiguration): FlagsClient {
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        val flagsFeature = FlagsFeature(mockSdkCore, configuration)
        whenever(mockSdkCore.createSingleThreadExecutorService(any())).thenReturn(mockExecutorService)
        whenever(mockSdkCore.getFeature(FLAGS_FEATURE_NAME)).thenReturn(mockFeatureScope)
        whenever(mockFeatureScope.dataStore).thenReturn(mockDataStore)
        whenever(mockFeatureScope.unwrap<FlagsFeature>()).thenReturn(flagsFeature)
        doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
        }.whenever(mockExecutorService).execute(any())
        doAnswer { invocation ->
            invocation.getArgument<(DatadogContext) -> Unit>(1).invoke(mockDatadogContext)
        }.whenever(mockFeatureScope).withContext(any(), any())
        return FlagsClient.Builder(sdkCore = mockSdkCore).build()
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 2_000L
        const val CLIENT_TOKEN = "client-token"
        const val ENVIRONMENT = "test"
        const val SDK_VERSION = "1.0.0"
    }
}
