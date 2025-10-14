/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.trace.core.propagation

import com.datadog.trace.core.DDSpanContext
import com.datadog.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class HttpCodecTest {

    @Mock
    private lateinit var mockContext: DDSpanContext

    @Test
    fun `M return correct baggage header W composeBaggage`(
        @StringForgery fakeUserId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeAccountId: String
    ) {
        // Given
        val expected = Baggage().apply {
            put(HttpCodec.BAGGAGE_USER_ID, fakeUserId)
            put(HttpCodec.BAGGAGE_ACCOUNT_ID, fakeAccountId)
            put(HttpCodec.BAGGAGE_SESSION_ID, fakeSessionId)
        }.toString()

        whenever(mockContext.tags).thenReturn(
            mapOf<String, Any>(
                HttpCodec.RUM_KEY_SESSION_ID to fakeSessionId,
                HttpCodec.RUM_KEY_USER_ID to fakeUserId,
                HttpCodec.RUM_KEY_ACCOUNT_ID to fakeAccountId
            )
        )

        // When
        val actual = HttpCodec.composeBaggage(mockContext).toString()

        // Then
        assertThat(actual).isEqualTo(expected)
    }
}
