/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.impl.internal.DatadogScopeAdapter
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@Extensions(
    ExtendWith(MockitoExtension::class)
)
class DatadogScopeAdapterTest {
    @Mock
    lateinit var delegate: AgentScope

    @Test
    fun `M delegate close W close is called`() {
        // Given
        val adapter = DatadogScopeAdapter(delegate)

        // When
        adapter.close()

        // Then
        verify(delegate).close()
    }
}
