/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import com.datadog.opentelemetry.context.OtelContext
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.context.Scope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogContextStorageTest {

    lateinit var testedStorage: DatadogContextStorage

    @Mock
    lateinit var mockWrappedStorage: ContextStorage

    @Mock
    lateinit var mockCurrentContext: Context

    @BeforeEach
    fun `set up`() {
        whenever(mockWrappedStorage.current()).thenReturn(mockCurrentContext)
        testedStorage = DatadogContextStorage(mockWrappedStorage)
    }

    // region current

    @Test
    fun `M return OtelContext W current { current context is not null }`() {
        // When
        val current = testedStorage.current()

        // Then
        assertThat(current).isInstanceOf(OtelContext::class.java)
        assertThat((current as OtelContext).wrapped).isSameAs(mockCurrentContext)
    }

    @Test
    fun `M return OtelContext W current { current context is null }`() {
        // Given
        whenever(mockWrappedStorage.current()).thenReturn(null)

        // When
        val current = testedStorage.current()

        // Then
        assertThat(current).isInstanceOf(OtelContext::class.java)
        assertThat((current as OtelContext).wrapped).isSameAs(Context.root())
    }

    // endregion current

    // region attach

    @Test
    fun `M delgate to the wrapper W attach { }`() {
        // Given
        val mockScope: Scope = mock()
        val mockContext: Context = mock()
        whenever(mockWrappedStorage.attach(mockContext)).thenReturn(mockScope)

        // When
        val scope = testedStorage.attach(mockContext)

        // Then
        assertThat(scope).isSameAs(mockScope)
    }

    // endregion
}
