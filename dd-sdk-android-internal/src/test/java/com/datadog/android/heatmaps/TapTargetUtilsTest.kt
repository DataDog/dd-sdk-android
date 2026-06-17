/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.heatmaps

import android.view.View
import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TapTargetUtilsTest {

    @Test
    fun `M return true W isValidTapTarget() {clickable and VISIBLE}`() {
        // Given
        val fakeView: View = mock()
        whenever(fakeView.isClickable).thenReturn(true)
        whenever(fakeView.visibility).thenReturn(View.VISIBLE)

        // When / Then
        assertThat(fakeView.isValidTapTarget()).isTrue()
    }

    @Test
    fun `M return false W isValidTapTarget() {clickable and INVISIBLE}`() {
        // Given
        val fakeView: View = mock()
        whenever(fakeView.isClickable).thenReturn(true)
        whenever(fakeView.visibility).thenReturn(View.INVISIBLE)

        // When / Then
        assertThat(fakeView.isValidTapTarget()).isFalse()
    }

    @Test
    fun `M return false W isValidTapTarget() {clickable and GONE}`() {
        // Given
        val fakeView: View = mock()
        whenever(fakeView.isClickable).thenReturn(true)
        whenever(fakeView.visibility).thenReturn(View.GONE)

        // When / Then
        assertThat(fakeView.isValidTapTarget()).isFalse()
    }

    @Test
    fun `M return false W isValidTapTarget() {not clickable and VISIBLE}`() {
        // Given
        val fakeView: View = mock()
        whenever(fakeView.isClickable).thenReturn(false)
        whenever(fakeView.visibility).thenReturn(View.VISIBLE)

        // When / Then
        assertThat(fakeView.isValidTapTarget()).isFalse()
    }

    @Test
    fun `M return true W isValidTapTarget() {disabled but clickable and VISIBLE}`() {
        // Given
        val fakeView: View = mock()
        whenever(fakeView.isEnabled).thenReturn(false)
        whenever(fakeView.isClickable).thenReturn(true)
        whenever(fakeView.visibility).thenReturn(View.VISIBLE)

        // When / Then
        assertThat(fakeView.isValidTapTarget()).isTrue()
    }
}
