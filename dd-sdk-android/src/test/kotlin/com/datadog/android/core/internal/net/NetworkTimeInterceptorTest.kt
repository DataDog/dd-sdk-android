/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.core.internal.time.MutableTimeProvider
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
internal class NetworkTimeInterceptorTest {

    lateinit var testedInterceptor: Interceptor

    @Mock
    lateinit var mockChain: Interceptor.Chain
    @Mock
    lateinit var mockTimeProvider: MutableTimeProvider

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response
    lateinit var fakeDateHeaderKey: String

    private val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeUrl = forge.aStringMatching("http://[a-z0-9_]{8}\\.[a-z]{3}")
        fakeRequest = Request.Builder().url(fakeUrl).build()
        fakeDateHeaderKey = forge.randomizeCase("date")

        testedInterceptor = NetworkTimeInterceptor(mockTimeProvider)
    }

    @Test
    fun `uses Date header to update offset`(
        @Forgery localDate: Date,
        @Forgery serverDate: Date,
        @LongForgery(min = 10, max = 5000) transportDelay: Long
    ) {
        val expectedOffset = serverDate.time - localDate.time
        whenever(mockTimeProvider.getDeviceTimestamp())
            .doReturn(localDate.time - transportDelay, localDate.time + transportDelay)
        setupFakeResponse(formatter.format(serverDate))

        val response = testedInterceptor.intercept(mockChain)

        argumentCaptor<Long>().apply {
            verify(mockTimeProvider).updateOffset(capture())
            assertThat(lastValue)
                // because the date header doesn't show millisecond
                // there can be up to a second offset with expected value
                .isCloseTo(expectedOffset, Offset.offset(1000L))
        }
        assertThat(response)
            .isSameAs(fakeResponse)
    }

    @Test
    fun `ignores invalid Date header`(forge: Forge) {
        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn forge.aLong()
        setupFakeResponse(forge.anAlphabeticalString())

        val response = testedInterceptor.intercept(mockChain)

        verify(mockTimeProvider, never()).updateOffset(anyOrNull())
        assertThat(response)
            .isSameAs(fakeResponse)
    }

    @Test
    fun `ignores absent Date header`(forge: Forge) {
        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn forge.aLong()
        setupFakeResponse(null)

        testedInterceptor.intercept(mockChain)

        verify(mockTimeProvider, never()).updateOffset(anyOrNull())
    }

    @Test
    fun `ignores timeout`(forge: Forge) {
        whenever(mockTimeProvider.getDeviceTimestamp()) doReturn forge.aLong()
        setupFakeResponse(null)
        whenever(mockChain.proceed(any())) doThrow SocketTimeoutException()

        assertThrows<SocketTimeoutException> {
            testedInterceptor.intercept(mockChain)
        }

        verify(mockTimeProvider, never()).updateOffset(anyOrNull())
    }

    // region Internal

    private fun setupFakeResponse(
        serverDateStr: String?
    ) {

        val builder = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("{}")

        fakeResponse = if (serverDateStr == null) {
            builder.build()
        } else {
            builder.header("date", serverDateStr).build()
        }

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse
    }

    // endregion
}
