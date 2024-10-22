/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.graphics.Point
import android.graphics.Rect
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class TouchPrivacyManagerTest {
    private lateinit var testedManager: TouchPrivacyManager

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeGlobalPrivacy = forge.aValueFrom(TouchPrivacy::class.java)
        testedManager = TouchPrivacyManager(fakeGlobalPrivacy)
    }

    @Test
    fun `M add to nextOverrideAreas W addTouchOverrideArea()`(
        forge: Forge
    ) {
        // Given
        val fakePrivacyOverride = forge.aValueFrom(TouchPrivacy::class.java)
        val mockOverrideArea = mock<Rect>()

        // When
        testedManager.addTouchOverrideArea(mockOverrideArea, fakePrivacyOverride)

        // Then
        assertThat(testedManager.getNextOverrideAreas()[mockOverrideArea]).isEqualTo(fakePrivacyOverride)
    }

    @Test
    fun `M replace currentAreas W updateCurrentTouchOverrideAreas()`(
        forge: Forge
    ) {
        // Given
        val fakePrivacyOverride = forge.aValueFrom(TouchPrivacy::class.java)
        val mockOverrideArea = mock<Rect>()
        testedManager.addTouchOverrideArea(mockOverrideArea, fakePrivacyOverride)
        assertThat(testedManager.getNextOverrideAreas()[mockOverrideArea]).isEqualTo(fakePrivacyOverride)

        // When
        testedManager.updateCurrentTouchOverrideAreas()

        // Then
        assertThat(testedManager.getCurrentOverrideAreas()[mockOverrideArea]).isEqualTo(fakePrivacyOverride)
        assertThat(testedManager.getNextOverrideAreas()).isEmpty()
    }

    @Test
    fun `M return override W shouldRecordTouch() { within override area }`(
        forge: Forge
    ) {
        // Given
        testedManager = TouchPrivacyManager(TouchPrivacy.HIDE)
        val fakePrivacyOverride = TouchPrivacy.SHOW
        val touchLocation = Point(
            forge.aPositiveInt(),
            forge.aPositiveInt()
        )

        val overrideArea = Rect(
            touchLocation.x - forge.aPositiveInt(),
            touchLocation.y - forge.aPositiveInt(),
            touchLocation.x + forge.aPositiveInt(),
            touchLocation.y + forge.aPositiveInt()
        )

        testedManager.addTouchOverrideArea(overrideArea, fakePrivacyOverride)
        testedManager.updateCurrentTouchOverrideAreas()

        // Then
        assertThat(testedManager.shouldRecordTouch(touchLocation)).isTrue()
    }

    @Test
    fun `M use global privacy W shouldRecordTouch() { outside override area }`(
        forge: Forge
    ) {
        // Given
        testedManager = TouchPrivacyManager(TouchPrivacy.SHOW)
        val fakeTouchX = forge.aPositiveInt()
        val fakeTouchY = forge.aPositiveInt()
        val fakePoint = mock<Point>()
        fakePoint.x = fakeTouchX
        fakePoint.y = fakeTouchY

        val fakeOverrideArea = Rect(
            fakeTouchX + 1,
            fakeTouchY + 1,
            fakeTouchX + 100,
            fakeTouchY + 100
        )

        testedManager.addTouchOverrideArea(fakeOverrideArea, TouchPrivacy.HIDE)
        testedManager.updateCurrentTouchOverrideAreas()

        // Then
        assertThat(testedManager.shouldRecordTouch(fakePoint)).isTrue()
    }

    @Test
    fun `M return false W shouldRecordTouch { matches both HIDE and SHOW }`(
        forge: Forge
    ) {
        // Given
        val touchLocation = Point(
            forge.aPositiveInt(),
            forge.aPositiveInt()
        )

        val hiddenTouchArea = Rect(
            touchLocation.x - forge.aPositiveInt(),
            touchLocation.y - forge.aPositiveInt(),
            touchLocation.x + forge.aPositiveInt(),
            touchLocation.y + forge.aPositiveInt()
        )

        val shownTouchArea = Rect(
            touchLocation.x - forge.aPositiveInt(),
            touchLocation.y - forge.aPositiveInt(),
            touchLocation.x + forge.aPositiveInt(),
            touchLocation.y + forge.aPositiveInt()
        )

        testedManager.addTouchOverrideArea(hiddenTouchArea, TouchPrivacy.HIDE)
        testedManager.addTouchOverrideArea(shownTouchArea, TouchPrivacy.SHOW)
        testedManager.updateCurrentTouchOverrideAreas()

        // Then
        assertThat(testedManager.shouldRecordTouch(touchLocation)).isFalse()
    }
}
