/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.tests

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.sdk.integration.network.models.TestRequest
import com.datadog.android.sdk.integration.network.rules.NetworkInstrumentationTestRule
import com.datadog.android.sdk.integration.network.utils.ExecutionResultComparisonAssert.Companion.assertThat
import com.datadog.android.sdk.integration.network.utils.NetworkTestConfig
import com.datadog.android.sdk.integration.network.utils.NetworkTestConfig.ALLOWED_METHODS
import com.datadog.android.sdk.integration.network.utils.NetworkTestConfig.asyncTest
import com.datadog.android.sdk.integration.network.wrappers.CompositeHttpClientWrapper
import com.datadog.android.sdk.integration.network.wrappers.cronet.CronetClientWrapper
import com.datadog.android.sdk.integration.network.wrappers.okhttp.OkHttpClientWrapper
import com.datadog.android.sdk.rules.Repeat
import com.datadog.android.sdk.rules.RepeatedTestRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests comparing network instrumentation behavior across HTTP clients.
 *
 * Uses [CompositeHttpClientWrapper] to execute requests through all available clients
 * and verify that they produce equivalent spans.
 *
 * Currently supported clients:
 * - OkHttp (with TracingInterceptor as Network Interceptor)
 * - Cronet (with DatadogCronetEngine.enableNetworkTracing)
 *
 * To add a new client:
 * 1. Create a new [com.datadog.android.sdk.integration.network.wrappers.HttpTestClientWrapper] implementation
 * 2. Add it to [CompositeHttpClientWrapper].
 */
@RunWith(RepeatedTestRunner::class)
internal class NetworkInstrumentationComparisonTest {

    @get:Rule
    val networkRule = NetworkInstrumentationTestRule()

    private lateinit var compositeClient: CompositeHttpClientWrapper

    @Before
    fun setUp() {
        compositeClient = CompositeHttpClientWrapper(
            listOf(
                OkHttpClientWrapper(baseUrl = networkRule.baseUrl),
                CronetClientWrapper(
                    context = InstrumentationRegistry.getInstrumentation().targetContext,
                    baseUrl = networkRule.baseUrl
                )
            )
        )
    }

    @After
    fun tearDown() {
        compositeClient.shutdown()
    }

    @Test
    fun executionResultsMustBeSimilarWhen_success() = asyncTest {
        // Given
        val method = networkRule.forge.anElementFrom(ALLOWED_METHODS)
        val url = NetworkTestConfig.Endpoint.forMethod(method)
        val request = TestRequest(
            url = url,
            method = method,
            body = NetworkTestConfig.Body.forMethod(method)
        )

        // When
        val results = compositeClient.execute(request)

        // Then
        assertThat(results)
            .haveSameSpanCount()
            .haveExpectedClients()
            .haveSameSpanStructure()
            .haveRequestUrl(url)
            .haveRequestMethod(method)
            .haveResponseStatusCode(HttpSpec.StatusCode.OK)
    }

    @Test
    fun executionResultsMustBeSimilarWhen_clientError() = asyncTest {
        // Given
        val method = networkRule.forge.anElementFrom(ALLOWED_METHODS)
        val error = networkRule.forge.anElementFrom(HttpSpec.StatusCode.clientErrors())
        val url = NetworkTestConfig.Endpoint.error(error, method)
        val request = TestRequest(
            url = url,
            method = method,
            body = NetworkTestConfig.Body.forMethod(method)
        )

        // When
        val results = compositeClient.execute(request)

        // Then
        assertThat(results)
            .haveSameSpanCount()
            .haveExpectedClients()
            .haveSameSpanStructure()
            .haveRequestUrl(url)
            .haveRequestMethod(method)
            .haveResponseStatusCode(error)
    }

    @Test
    fun executionResultsMustBeSimilarWhen_serverError() = asyncTest {
        // Given
        val method = networkRule.forge.anElementFrom(ALLOWED_METHODS)
        val error = networkRule.forge.anElementFrom(HttpSpec.StatusCode.serverErrors())
        val url = NetworkTestConfig.Endpoint.error(error, method)
        val request = TestRequest(
            url = url,
            method = method,
            body = NetworkTestConfig.Body.forMethod(method)
        )

        // When
        val results = compositeClient.execute(request)

        // Then
        assertThat(results)
            .haveSameSpanCount()
            .haveExpectedClients()
            .haveSameSpanStructure()
            .haveRequestUrl(url)
            .haveRequestMethod(method)
            .haveResponseStatusCode(error)
    }

    @Test
    @Repeat(10)
    fun executionResultsMustBeSimilarWhen_redirect() = asyncTest {
        // Given
        val hopsCount = networkRule.forge.anInt(min = 1, max = 5)
        val method = networkRule.forge.anElementFrom(ALLOWED_METHODS)
        val url = NetworkTestConfig.Endpoint.redirect(hopsCount, method)
        val request = TestRequest(
            url = url,
            method = method,
            body = NetworkTestConfig.Body.forMethod(method)
        )

        // When
        val results = compositeClient.execute(request)

        // Then
        assertThat(results)
            .haveSameSpanCount()
            .haveSameStatusCode()
            .haveExpectedClients()
            .haveSameSpanStructure()
            .haveRequestUrl(url)
            .haveRequestMethod(method)
    }

    @Test
    @Repeat(10)
    fun executionResultsMustBeSimilarWhen_rateLimited() = asyncTest {
        // Given
        val method = HttpSpec.Method.GET
        val url = NetworkTestConfig.Endpoint.retry(method)
        val request = TestRequest(
            url = url,
            method = method,
            body = NetworkTestConfig.Body.forMethod(method)
        )

        // When
        val results = compositeClient.execute(request)

        // Then
        assertThat(results)
            .haveSameSpanCount()
            .haveExpectedClients()
            .haveSameSpanStructure()
            .haveSameStatusCode()
            .haveRequestUrl(url)
            .haveRequestMethod(method)
    }
}
