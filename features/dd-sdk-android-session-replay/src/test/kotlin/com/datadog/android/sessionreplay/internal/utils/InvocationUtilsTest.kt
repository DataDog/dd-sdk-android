/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.lang.Exception

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class InvocationUtilsTest {
    private lateinit var testedInvocationUtils: InvocationUtils

    @Mock
    lateinit var mockLogger: InternalLogger

    @BeforeEach
    fun setup() {
        testedInvocationUtils = InvocationUtils()
    }

    @Test
    fun `M call function W safeCallWithErrorLogging()`() {
        // Given
        val mockFunc = mock<() -> Unit>()

        // When
        testedInvocationUtils.safeCallWithErrorLogging(
            logger = mockLogger,
            call = { mockFunc() },
            failureMessage = "someMessage"
        )

        // Then
        verify(mockFunc, times(1))()
    }

    @Test
    fun `M return function result W safeCallWithErrorLogging()`() {
        // Given
        val mockFunc = mock<() -> Boolean>()
        whenever(mockFunc()).thenReturn(true)

        // When
        val result = testedInvocationUtils.safeCallWithErrorLogging(
            logger = mockLogger,
            call = { mockFunc() },
            failureMessage = "someMessage"
        )

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M log failureMessage W safeCallWithErrorLogging() { failure }`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val mockFunc = mock<() -> Boolean>()
        val mockException: Exception = mock()
        doThrow(mockException).`when`(mockFunc)

        val captor = argumentCaptor<() -> String>()

        // When
        testedInvocationUtils.safeCallWithErrorLogging(
            logger = mockLogger,
            call = { mockFunc() },
            failureMessage = fakeMessage
        )

        // Then
        verify(mockLogger).log(
            level = any(),
            target = any(),
            captor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(captor.firstValue()).isEqualTo(fakeMessage)
    }
}
