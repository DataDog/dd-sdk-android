/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CombinedInterceptorTest {

    lateinit var testedInterceptor: Interceptor

    @Mock
    lateinit var mockDelegate1: RequestInterceptor

    @Mock
    lateinit var mockDelegate2: RequestInterceptor

    @Mock
    lateinit var mockDelegate3: RequestInterceptor

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockRequest: Request

    @Mock
    lateinit var mockRequest1: Request

    @Mock
    lateinit var mockRequest2: Request

    @Mock
    lateinit var mockRequest3: Request

    @Mock
    lateinit var mockResponse: Response

    @Forgery
    lateinit var fakeThrowable: Throwable

    @BeforeEach
    fun `set up`() {
        whenever(mockChain.request()) doReturn mockRequest
    }

    // region Single delegate

    @Test
    fun `handles successful request`() {
        testedInterceptor = CombinedInterceptor(mockDelegate1)
        whenever(mockDelegate1.transformRequest(mockRequest)) doReturn mockRequest1
        whenever(mockChain.proceed(mockRequest1)) doReturn mockResponse

        testedInterceptor.intercept(mockChain)

        inOrder(mockDelegate1) {
            verify(mockDelegate1).transformRequest(mockRequest)
            verify(mockDelegate1).handleResponse(mockRequest1, mockResponse)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `handles throwing request`() {
        testedInterceptor = CombinedInterceptor(mockDelegate1)
        whenever(mockDelegate1.transformRequest(mockRequest)) doReturn mockRequest1
        whenever(mockChain.proceed(mockRequest1)) doThrow fakeThrowable

        assertThrows(fakeThrowable.javaClass) {
            testedInterceptor.intercept(mockChain)
        }

        inOrder(mockDelegate1) {
            verify(mockDelegate1).transformRequest(mockRequest)
            verify(mockDelegate1).handleThrowable(mockRequest1, fakeThrowable)
            verifyNoMoreInteractions()
        }
    }

    // endregion

    // region Single delegate

    @Test
    fun `handles successful request multiple delegates`() {
        testedInterceptor = CombinedInterceptor(listOf(mockDelegate1, mockDelegate2, mockDelegate3))
        whenever(mockDelegate1.transformRequest(mockRequest)) doReturn mockRequest1
        whenever(mockDelegate2.transformRequest(mockRequest1)) doReturn mockRequest2
        whenever(mockDelegate3.transformRequest(mockRequest2)) doReturn mockRequest3
        whenever(mockChain.proceed(mockRequest3)) doReturn mockResponse

        testedInterceptor.intercept(mockChain)

        inOrder(mockDelegate1, mockDelegate2, mockDelegate3) {
            verify(mockDelegate1).transformRequest(mockRequest)
            verify(mockDelegate2).transformRequest(mockRequest1)
            verify(mockDelegate3).transformRequest(mockRequest2)
            verify(mockDelegate1).handleResponse(mockRequest3, mockResponse)
            verify(mockDelegate2).handleResponse(mockRequest3, mockResponse)
            verify(mockDelegate3).handleResponse(mockRequest3, mockResponse)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `handles throwing request multiple delegates`() {
        testedInterceptor = CombinedInterceptor(listOf(mockDelegate1, mockDelegate2, mockDelegate3))
        whenever(mockDelegate1.transformRequest(mockRequest)) doReturn mockRequest1
        whenever(mockDelegate2.transformRequest(mockRequest1)) doReturn mockRequest2
        whenever(mockDelegate3.transformRequest(mockRequest2)) doReturn mockRequest3
        whenever(mockChain.proceed(mockRequest3)) doThrow fakeThrowable

        assertThrows(fakeThrowable.javaClass) {
            testedInterceptor.intercept(mockChain)
        }

        inOrder(mockDelegate1, mockDelegate2, mockDelegate3) {
            verify(mockDelegate1).transformRequest(mockRequest)
            verify(mockDelegate2).transformRequest(mockRequest1)
            verify(mockDelegate3).transformRequest(mockRequest2)
            verify(mockDelegate1).handleThrowable(mockRequest3, fakeThrowable)
            verify(mockDelegate2).handleThrowable(mockRequest3, fakeThrowable)
            verify(mockDelegate3).handleThrowable(mockRequest3, fakeThrowable)
            verifyNoMoreInteractions()
        }
    }

    // endregion
}
