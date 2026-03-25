/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.tests.elmyr.aUrlWithQueryParams
import com.datadog.android.tests.elmyr.anOkHttpRequest
import com.datadog.android.tests.elmyr.anOkHttpResponse
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CurlInterceptorTest {

    lateinit var testedInterceptor: Interceptor

    @Mock
    lateinit var mockChain: Interceptor.Chain

    @Mock
    lateinit var mockOutput: (String) -> Unit

    lateinit var fakeUrl: String

    lateinit var fakeRequest: Request
    lateinit var fakeResponse: Response

    @StringForgery
    lateinit var fakeBody: String

    lateinit var forge: Forge

    @BeforeEach
    fun `set up`(forge: Forge) {
        this.forge = forge
        fakeUrl = forge.aUrlWithQueryParams().fullUrl
    }

    @Test
    fun `M output curl command W intercept() {GET}`(@BoolForgery withBody: Boolean) {
        // Given
        testedInterceptor = CurlInterceptor(withBody, mockOutput)
        fakeRequest = forge.anOkHttpRequest(fakeUrl)
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(fakeRequest)
        assertThat(response).isSameAs(fakeResponse)
        verify(mockOutput).invoke("curl -X GET \"$fakeUrl\"")
    }

    @Test
    fun `M output curl command W intercept() {POST, no body}`() {
        // Given
        testedInterceptor = CurlInterceptor(false, mockOutput)
        fakeRequest = forge.anOkHttpRequest(fakeUrl) {
            post(fakeBody.toByteArray().toRequestBody(null))
        }
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(fakeRequest)
        assertThat(response).isSameAs(fakeResponse)
        verify(mockOutput).invoke("curl -X POST \"$fakeUrl\"")
    }

    @Test
    fun `M output curl command W intercept() {POST, body}`() {
        // Given
        testedInterceptor = CurlInterceptor(true, mockOutput)
        fakeRequest = forge.anOkHttpRequest(fakeUrl) {
            post(fakeBody.toByteArray().toRequestBody(null))
        }
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(fakeRequest)
        assertThat(response).isSameAs(fakeResponse)
        verify(mockOutput).invoke("curl -X POST -d '$fakeBody' \"$fakeUrl\"")
    }

    @Test
    fun `M output curl command W intercept() {GET with headers}`(
        @BoolForgery withBody: Boolean,
        @StringForgery(StringForgeryType.ALPHABETICAL) headerName: String,
        @StringForgery headerValue: String
    ) {
        // Given
        testedInterceptor = CurlInterceptor(withBody, mockOutput)
        fakeRequest = forge.anOkHttpRequest(fakeUrl) {
            addHeader(headerName, headerValue)
        }
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(fakeRequest)
        assertThat(response).isSameAs(fakeResponse)
        verify(mockOutput).invoke(
            "curl -X GET -H \"${headerName.lowercase(Locale.US)}:$headerValue\" \"$fakeUrl\""
        )
    }

    @Test
    fun `M output curl command W intercept() {POST, content type}`(
        @StringForgery type: String,
        @StringForgery subtype: String
    ) {
        // Given
        testedInterceptor = CurlInterceptor(true, mockOutput)
        fakeRequest = forge.anOkHttpRequest(fakeUrl) {
            post(fakeBody.toByteArray().toRequestBody("$type/$subtype".toMediaTypeOrNull()))
        }
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(fakeRequest)
        assertThat(response).isSameAs(fakeResponse)
        verify(mockOutput)
            .invoke("curl -X POST -H \"Content-Type:$type/$subtype\" -d '$fakeBody' \"$fakeUrl\"")
    }

    @Test
    fun `M output curl command W intercept() {POST, multipart body}`(
        @StringForgery type: String,
        @StringForgery subtype: String,
        @StringForgery fakeFormKey: String,
        @StringForgery fakeFormKeyValue: String
    ) {
        // Given
        testedInterceptor = CurlInterceptor(true, mockOutput)
        fakeRequest = forge.anOkHttpRequest(fakeUrl) {
            post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(fakeFormKey, fakeFormKeyValue)
                    .addPart(
                        fakeBody.toByteArray()
                            .toRequestBody("$type/$subtype".toMediaTypeOrNull())
                    )
                    .build()
            )
        }
        stubChain()

        // When
        val response = testedInterceptor.intercept(mockChain)

        // Then
        verify(mockChain).proceed(fakeRequest)
        assertThat(response).isSameAs(fakeResponse)
        val fakeEscapedUrl = fakeUrl
            .replace("/", "\\/")
            .replace("?", "\\?")
        val expectedOutput = "curl -X POST -H \"Content-Type:multipart\\/form-data; " +
            "boundary=(.*) -H \"content-disposition:\\[form\\-data; name=\"$fakeFormKey\"]\" " +
            "-d '$fakeFormKeyValue' -d '$fakeBody' \"$fakeEscapedUrl\""
        val argumentCaptor = argumentCaptor<String>()
        verify(mockOutput).invoke(argumentCaptor.capture())
        assertThat(argumentCaptor.firstValue).matches(expectedOutput)
    }

    // region Internal

    private fun stubChain() {
        fakeResponse = forge.anOkHttpResponse(fakeRequest, forge.anInt(min = 200, max = 600))

        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doReturn fakeResponse
    }

    // endregion
}
