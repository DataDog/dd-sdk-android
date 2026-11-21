/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultDrawableCopierTest {

    private lateinit var testedCopier: DefaultDrawableCopier

    @Mock
    private lateinit var mockResources: Resources

    @Mock
    private lateinit var mockConstantState: ConstantState

    @Mock
    private lateinit var mockDrawable: Drawable

    @Mock
    private lateinit var mockCopy: Drawable

    @BeforeEach
    fun `set up`() {
        whenever(mockDrawable.constantState) doReturn mockConstantState
        whenever(mockConstantState.newDrawable(mockResources)) doReturn mockCopy
        testedCopier = DefaultDrawableCopier()
    }

    @Test
    fun `M return the copy W copy()`() {
        // When
        val result = testedCopier.copy(mockDrawable, mockResources)

        // Then
        assertThat(result).isSameAs(mockCopy)
    }

    @Test
    fun `M return null W copy() { drawable has no constant state }`() {
        // Given
        whenever(mockDrawable.constantState) doReturn null

        // When
        val result = testedCopier.copy(mockDrawable, mockResources)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W copy() { constant state returns the original drawable }`() {
        // Given
        whenever(mockConstantState.newDrawable(mockResources)) doReturn mockDrawable

        // When
        val result = testedCopier.copy(mockDrawable, mockResources)

        // Then
        assertThat(result).isNull()
    }
}
