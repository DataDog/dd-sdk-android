/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewUtilsTest {

    @Test
    fun `M correctly resolve the View global bounds W resolveViewGlobalBounds`(forge: Forge) {
        // Given
        val fakeGlobalX = forge.anInt()
        val fakeGlobalY = forge.anInt()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        val mockView: View = mock {
            whenever(it.getLocationOnScreen(any())).thenAnswer {
                val coords = it.arguments[0] as IntArray
                coords[0] = fakeGlobalX
                coords[1] = fakeGlobalY
                null
            }
            whenever(it.width).thenReturn(fakeWidth)
            whenever(it.height).thenReturn(fakeHeight)
        }
        val fakePixelDensity = forge.aPositiveFloat()

        // When
        val globalBounds = ViewUtils.resolveViewGlobalBounds(mockView, fakePixelDensity)

        // Then
        assertThat(globalBounds.x)
            .isEqualTo(fakeGlobalX.densityNormalized(fakePixelDensity).toLong())
        assertThat(globalBounds.y)
            .isEqualTo(fakeGlobalY.densityNormalized(fakePixelDensity).toLong())
        assertThat(globalBounds.width)
            .isEqualTo(fakeWidth.densityNormalized(fakePixelDensity).toLong())
        assertThat(globalBounds.height)
            .isEqualTo(fakeHeight.densityNormalized(fakePixelDensity).toLong())
    }
}
