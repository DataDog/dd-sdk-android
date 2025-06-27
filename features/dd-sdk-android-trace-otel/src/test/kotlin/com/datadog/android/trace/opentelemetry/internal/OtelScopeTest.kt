/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry.internal

import com.datadog.android.trace.api.scope.DatadogScope
import io.opentelemetry.context.Scope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class OtelScopeTest {

    @Mock
    lateinit var mockWrappedScope: Scope

    @Mock
    lateinit var mockAgentScope: DatadogScope

    lateinit var testedScope: OtelScope

    @BeforeEach
    fun `set up`() {
        testedScope = OtelScope(mockWrappedScope, mockAgentScope)
    }

    @Test
    fun `M call close on wrapped scope W close`() {
        // When
        testedScope.close()

        // Then
        verify(mockWrappedScope).close()
    }

    @Test
    fun `M call close on agent scope W close`() {
        // When
        testedScope.close()

        // Then
        verify(mockAgentScope).close()
    }
}
