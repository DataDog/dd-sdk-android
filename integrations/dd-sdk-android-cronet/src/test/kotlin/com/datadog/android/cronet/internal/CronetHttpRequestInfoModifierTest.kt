/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.tests.elmyr.URL_FORGERY_PATTERN
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.quality.Strictness
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CronetHttpRequestInfoModifierTest {

    @Mock
    lateinit var mockExecutor: Executor

    @Mock
    lateinit var mockEngine: DatadogCronetEngine

    @Mock
    lateinit var mockCallback: CronetRequestCallback

    @StringForgery(regex = URL_FORGERY_PATTERN)
    lateinit var fakeUrl: String

    private lateinit var fakeRequestInfo: CronetHttpRequestInfo
    private lateinit var testedRequestInfoBuilder: CronetHttpRequestInfoBuilder

    @BeforeEach
    fun `set up`(forge: Forge) {
        val requestContext = CronetRequestContext(
            url = fakeUrl,
            engine = mockEngine,
            requestCallback = mockCallback,
            executor = mockExecutor
        ).apply { setHttpMethod(forge.anElementFrom(HttpSpec.Method.values())) }
        fakeRequestInfo = CronetHttpRequestInfo(requestContext)
        testedRequestInfoBuilder = fakeRequestInfo.newBuilder()
    }

    @Test
    fun `M update url W setUrl()`(
        @StringForgery(regex = URL_FORGERY_PATTERN) newUrl: String
    ) {
        // When
        testedRequestInfoBuilder.setUrl(newUrl)
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.url).isEqualTo(newUrl)
    }

    @Test
    fun `M add single header W addHeader() { single value }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue: String
    ) {
        // When
        testedRequestInfoBuilder.addHeader(headerKey, headerValue)
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.headers[headerKey]).containsExactly(headerValue)
    }

    @Test
    fun `M add multiple values W addHeader() { multiple values }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue1: String,
        @StringForgery headerValue2: String,
        @StringForgery headerValue3: String
    ) {
        // When
        testedRequestInfoBuilder.addHeader(headerKey, headerValue1, headerValue2, headerValue3)
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.headers[headerKey]).containsExactly(headerValue1, headerValue2, headerValue3)
    }

    @Test
    fun `M append values W addHeader() { called multiple times }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue1: String,
        @StringForgery headerValue2: String
    ) {
        // When
        testedRequestInfoBuilder.addHeader(headerKey, headerValue1)
        testedRequestInfoBuilder.addHeader(headerKey, headerValue2)
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.headers[headerKey]).containsExactly(headerValue1, headerValue2)
    }

    @Test
    fun `M remove header W removeHeader()`(
        @StringForgery headerKey: String,
        @StringForgery headerValue: String
    ) {
        // Given
        testedRequestInfoBuilder.addHeader(headerKey, headerValue)

        // When
        val result = testedRequestInfoBuilder.removeHeader(headerKey).build()

        // Then
        assertThat(result.headers[headerKey]).isNull()
    }

    @Test
    fun `M remove all values W removeHeader() { multiple values }`(
        @StringForgery headerKey: String,
        @StringForgery headerValue1: String,
        @StringForgery headerValue2: String
    ) {
        // Given
        testedRequestInfoBuilder.addHeader(headerKey, headerValue1, headerValue2)

        // When
        val result = testedRequestInfoBuilder.removeHeader(headerKey).build()

        // Then
        assertThat(result.headers[headerKey]).isNull()
    }

    @Test
    fun `M not affect other headers W removeHeader()`(
        @StringForgery headerKey1: String,
        @StringForgery headerValue1: String,
        @StringForgery headerKey2: String,
        @StringForgery headerValue2: String
    ) {
        // Given
        testedRequestInfoBuilder.addHeader(headerKey1, headerValue1)
        testedRequestInfoBuilder.addHeader(headerKey2, headerValue2)

        // When
        val result = testedRequestInfoBuilder.removeHeader(headerKey1).build()

        // Then
        assertThat(result.headers[headerKey1]).isNull()
        assertThat(result.headers[headerKey2]).containsExactly(headerValue2)
    }

    @Test
    fun `M add tag W addTag()`(forge: Forge) {
        // Given
        val fakeTag = FakeTag(forge.anAlphabeticalString())

        // When
        testedRequestInfoBuilder.addTag(FakeTag::class.java, fakeTag)
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.tag(FakeTag::class.java)).isEqualTo(fakeTag)
    }

    @Test
    fun `M return CronetHttpRequestInfo W build()`() {
        // When
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result).isInstanceOf(CronetHttpRequestInfo::class.java)
    }

    @Test
    fun `M preserve original url W build() { no modifications }`() {
        // When
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.url).isEqualTo(fakeUrl)
    }

    @Test
    fun `M preserve original method W build() { no modifications }`() {
        // When
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.method).isEqualTo(fakeRequestInfo.method)
    }

    @Test
    fun `M update method W setMethod()`(forge: Forge) {
        // Given
        val newMethod = forge.anElementFrom(HttpSpec.Method.values().filter { it != fakeRequestInfo.method })

        // When
        val result = testedRequestInfoBuilder.setMethod(newMethod)
            .build()

        // Then
        assertThat(result.method).isEqualTo(newMethod)
    }

    @Test
    fun `M preserve original annotations W build()`(forge: Forge) {
        // Given
        val existingAnnotation = FakeTag(forge.anAlphabeticalString())
        testedRequestInfoBuilder.addTag(FakeTag::class.java, existingAnnotation)

        // When
        val intermediateBuilder = testedRequestInfoBuilder.build().newBuilder()
        intermediateBuilder.addTag(OtherFakeTag::class.java, OtherFakeTag(forge.anAlphabeticalString()))
        val result = intermediateBuilder.build()

        // Then
        assertThat(result.tag(FakeTag::class.java)).isEqualTo(existingAnnotation)
    }

    @Test
    fun `M replace header W replaceHeader()`(
        @StringForgery headerKey: String,
        @StringForgery oldValue: String,
        @StringForgery newValue: String
    ) {
        // Given
        testedRequestInfoBuilder.addHeader(headerKey, oldValue)

        // When
        val result = testedRequestInfoBuilder.replaceHeader(headerKey, newValue).build()

        // Then
        assertThat(result.headers[headerKey]).containsExactly(newValue)
    }

    @Test
    fun `M not add tag W addTag() { null value }`() {
        // When
        testedRequestInfoBuilder.addTag(FakeTag::class.java, null)
        val result = testedRequestInfoBuilder.build()

        // Then
        assertThat(result.tag(FakeTag::class.java)).isNull()
    }

    private data class FakeTag(val value: String)
    private data class OtherFakeTag(val value: String)
}
