/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

internal class CapturedViewMapperRegistryTest {

    private val mockInternalLogger: InternalLogger = mock()
    private val textViewMapper: CapturedViewMapper<TextView> = mock()
    private val fallbackMapper: CapturedViewMapper<View> = mock()
    private val testedRegistry = CapturedViewMapperRegistry(
        mappers = listOf(CapturedMapperTypeWrapper(TextView::class.java, textViewMapper)),
        fallbackMapper = fallbackMapper,
        internalLogger = mockInternalLogger
    )

    @Test
    fun `M resolve the matching mapper W resolve { TextView }`() {
        // Given
        val mockTextView: TextView = mock()

        // When
        val resolved = testedRegistry.resolve(mockTextView)

        // Then
        assertThat(resolved).isEqualTo(textViewMapper)
    }

    @Test
    fun `M resolve the fallback and log once W resolve { unmapped leaf view }`() {
        // Given
        val mockView: View = mock()

        // When
        val resolved = testedRegistry.resolve(mockView)

        // Then
        assertThat(resolved).isEqualTo(fallbackMapper)
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.INFO),
            target = eq(InternalLogger.Target.TELEMETRY),
            messageBuilder = any(),
            throwable = eq(null),
            onlyOnce = eq(true),
            additionalProperties = any()
        )
    }

    @Test
    fun `M resolve the fallback without logging W resolve { unmapped ViewGroup }`() {
        // Given
        val mockViewGroup: ViewGroup = mock()

        // When
        val resolved = testedRegistry.resolve(mockViewGroup)

        // Then
        assertThat(resolved).isEqualTo(fallbackMapper)
        verify(mockInternalLogger, never()).log(
            level = any(),
            target = any(),
            messageBuilder = any(),
            throwable = anyOrNull(),
            onlyOnce = any(),
            additionalProperties = anyOrNull()
        )
    }
}
