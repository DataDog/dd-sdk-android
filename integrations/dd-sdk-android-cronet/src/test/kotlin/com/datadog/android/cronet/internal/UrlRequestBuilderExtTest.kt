/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.cronet.internal

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.chromium.net.UrlRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UrlRequestBuilderExtTest {

    @Mock
    lateinit var mockBuilder: UrlRequest.Builder

    @Test
    fun `M add single header W addHeaders() {single key single value}`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue: String
    ) {
        // Given
        val headers = mapOf(fakeKey to listOf(fakeValue))

        // When
        mockBuilder.addHeaders(headers)

        // Then
        verify(mockBuilder).addHeader(fakeKey, fakeValue)
    }

    @Test
    fun `M add multiple values for same key W addHeaders() {single key multiple values}`(
        @StringForgery fakeKey: String,
        @StringForgery fakeValue1: String,
        @StringForgery fakeValue2: String,
        @StringForgery fakeValue3: String
    ) {
        // Given
        val headers = mapOf(fakeKey to listOf(fakeValue1, fakeValue2, fakeValue3))

        // When
        mockBuilder.addHeaders(headers)

        // Then
        val inOrder = inOrder(mockBuilder)
        inOrder.verify(mockBuilder).addHeader(fakeKey, fakeValue1)
        inOrder.verify(mockBuilder).addHeader(fakeKey, fakeValue2)
        inOrder.verify(mockBuilder).addHeader(fakeKey, fakeValue3)
    }

    @Test
    fun `M add headers for multiple keys W addHeaders() {multiple keys}`(
        @StringForgery fakeKey1: String,
        @StringForgery fakeKey2: String,
        @StringForgery fakeValue1: String,
        @StringForgery fakeValue2: String
    ) {
        // Given
        val headers = mapOf(
            fakeKey1 to listOf(fakeValue1),
            fakeKey2 to listOf(fakeValue2)
        )

        // When
        mockBuilder.addHeaders(headers)

        // Then
        verify(mockBuilder).addHeader(fakeKey1, fakeValue1)
        verify(mockBuilder).addHeader(fakeKey2, fakeValue2)
    }

    @Test
    fun `M not add any headers W addHeaders() {empty map}`() {
        // When
        mockBuilder.addHeaders(emptyMap<String, List<String>>())

        // Then
        verifyNoInteractions(mockBuilder)
    }

    @Test
    fun `M skip empty value lists W addHeaders() {key with empty list}`(
        @StringForgery fakeKey: String
    ) {
        // Given
        val headers = mapOf(fakeKey to emptyList<String>())

        // When
        mockBuilder.addHeaders(headers)

        // Then
        verifyNoInteractions(mockBuilder)
    }

    @Test
    fun `M add all headers from forge W addHeaders() {random headers}`(forge: Forge) {
        // Given
        val fakeHeaders = forge.aMap(size = forge.anInt(1, 5)) {
            forge.anAlphabeticalString() to forge.aList(size = forge.anInt(1, 3)) {
                forge.anAlphabeticalString()
            }
        }

        // When
        mockBuilder.addHeaders(fakeHeaders)

        // Then
        fakeHeaders.forEach { (key, values) ->
            values.forEach { value ->
                verify(mockBuilder).addHeader(key, value)
            }
        }
    }
}
