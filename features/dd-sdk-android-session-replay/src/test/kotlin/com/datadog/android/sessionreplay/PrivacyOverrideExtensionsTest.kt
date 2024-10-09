/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PrivacyOverrideExtensionsTest {

    @Test
    fun `M set tag W setSessionReplayHidden() { hide is true }`() {
        // Given
        val mockView = mock<View>()

        // When
        mockView.setSessionReplayHidden(true)

        // Then
        verify(mockView).setTag(eq(R.id.datadog_hidden), eq(true))
    }

    @Test
    fun `M set tag to null W setSessionReplayHidden() { hide is false }`() {
        // Given
        val mockView = mock<View>()

        // When
        mockView.setSessionReplayHidden(false)

        // Then
        verify(mockView).setTag(eq(R.id.datadog_hidden), isNull())
    }
}
