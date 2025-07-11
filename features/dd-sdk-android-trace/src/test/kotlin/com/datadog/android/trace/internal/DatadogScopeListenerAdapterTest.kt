/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.trace.api.scope.DataScopeListener
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@Extensions(
    ExtendWith(MockitoExtension::class)
)
class DatadogScopeListenerAdapterTest {

    @Mock
    lateinit var delegate: DataScopeListener

    @Test
    fun `M delegate afterScopeClosed W afterScopeClosed is called`() {
        // Given
        val adapter = DatadogScopeListenerAdapter(delegate)

        // When
        adapter.afterScopeClosed()

        // Then
        verify(delegate).afterScopeClosed()
    }

    @Test
    fun `M delegate afterScopeActivated W afterScopeActivated is called`() {
        // Then
        val adapter = DatadogScopeListenerAdapter(delegate)

        // When
        adapter.afterScopeActivated()

        // Then
        verify(delegate).afterScopeActivated()
    }
}
