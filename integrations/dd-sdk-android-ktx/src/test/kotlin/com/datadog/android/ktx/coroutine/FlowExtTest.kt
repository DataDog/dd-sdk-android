/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.coroutine

import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.v2.api.SdkCore
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowExtTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: SdkCore

    @BeforeEach
    fun `set up`() {
        GlobalRumMonitor.registerIfAbsent(mockSdkCore, mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `M add RUM Error W flow emits error`(
        @StringForgery message: String
    ) {
        // Given
        val throwable = IllegalStateException(message)
        val flow = flow<String> { throw (throwable) }

        // When
        assertThrows<IllegalStateException> {
            runBlocking {
                withContext(Dispatchers.IO) {
                    flow.sendErrorToDatadog(mockSdkCore)
                        .collect { println(it) }
                }
            }
        }

        // Then
        verify(mockRumMonitor).addError(
            ERROR_FLOW,
            RumErrorSource.SOURCE,
            throwable,
            emptyMap()
        )
    }

    @Test
    fun `M doNothing W flow emits successfully`(
        @StringForgery data: String
    ) {
        // Given
        val flow = flow { emit(data) }

        // When
        val result = mutableListOf<String>()
        runBlocking {
            withContext(Dispatchers.IO) {
                flow.sendErrorToDatadog(mockSdkCore)
                    .collect { result.add(it) }
            }
        }

        // Then
        assertThat(result).containsExactly(data)
        verifyNoInteractions(mockRumMonitor)
    }
}
