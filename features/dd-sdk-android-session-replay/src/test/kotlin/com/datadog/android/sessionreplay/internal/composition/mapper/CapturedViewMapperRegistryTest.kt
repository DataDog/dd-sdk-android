/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CapturedViewMapperRegistryTest {

    private val mockInternalLogger: InternalLogger = mock()
    private val mockTextViewMapper: CapturedViewMapper<TextView> = mock()
    private val mockFallbackMapper: CapturedViewMapper<View> = mock()
    private val testedRegistry = CapturedViewMapperRegistry(
        mappers = listOf(CapturedMapperTypeWrapper(TextView::class.java, mockTextViewMapper)),
        fallbackMapper = mockFallbackMapper,
        internalLogger = mockInternalLogger
    )

    @Test
    fun `M resolve the matching mapper W resolve { TextView }`() {
        // Given
        val mockTextView: TextView = mock()

        // When
        val resolved = testedRegistry.resolve(mockTextView)

        // Then
        assertThat(resolved).isEqualTo(mockTextViewMapper)
    }

    @Test
    fun `M resolve the fallback and log once per type W resolve { repeated unmapped leaf views }`() {
        // Given
        val mockView: View = mock()
        val mockAnotherView: View = mock()
        val fakeViewType = mockView.javaClass.canonicalName ?: mockView.javaClass.name

        // When
        val resolved = testedRegistry.resolve(mockView)
        val resolvedAgain = testedRegistry.resolve(mockView)
        val resolvedAnother = testedRegistry.resolve(mockAnotherView)

        // Then
        assertThat(resolved).isEqualTo(mockFallbackMapper)
        assertThat(resolvedAgain).isEqualTo(mockFallbackMapper)
        assertThat(resolvedAnother).isEqualTo(mockFallbackMapper)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.TELEMETRY,
            "No mapper found for view $fakeViewType",
            onlyOnce = true,
            additionalProperties = mapOf("replay.widget.type" to fakeViewType)
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M report each unmapped type with structured properties W resolve { distinct leaf types }`() {
        // Given
        val mockView: View = mock()
        val mockImageView: ImageView = mock()

        // When
        testedRegistry.resolve(mockView)
        testedRegistry.resolve(mockImageView)
        testedRegistry.resolve(mockView)
        testedRegistry.resolve(mockImageView)

        // Then
        listOf(mockView, mockImageView).forEach { mockLeafView ->
            val fakeViewType = mockLeafView.javaClass.canonicalName ?: mockLeafView.javaClass.name
            mockInternalLogger.verifyLog(
                InternalLogger.Level.INFO,
                InternalLogger.Target.TELEMETRY,
                "No mapper found for view $fakeViewType",
                onlyOnce = true,
                additionalProperties = mapOf("replay.widget.type" to fakeViewType)
            )
        }
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M resolve the fallback without logging W resolve { unmapped ViewGroup }`() {
        // Given
        val mockViewGroup: ViewGroup = mock()

        // When
        val resolved = testedRegistry.resolve(mockViewGroup)

        // Then
        assertThat(resolved).isEqualTo(mockFallbackMapper)
        verifyNoInteractions(mockInternalLogger)
    }
}
