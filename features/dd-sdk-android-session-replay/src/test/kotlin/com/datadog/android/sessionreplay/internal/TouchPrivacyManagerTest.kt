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
    fun `M add to nextAreasSnapshot W addTouchOverrideArea()`(
        forge: Forge
    ) {
        // Given
        val fakePrivacy = forge.aValueFrom(TouchPrivacy::class.java)
        val mockRect = mock<Rect>()

        // When
        testedManager.addTouchOverrideArea(mockRect, fakePrivacy)

        // Then
        assertThat(testedManager.nextOverrideSnapshot[mockRect]).isEqualTo(fakePrivacy)
    }

    @Test
    fun `M replace currentAreas W copyNextSnapshotToCurrentSnapshot()`(
        forge: Forge
    ) {
        // Given
        val fakePrivacy = forge.aValueFrom(TouchPrivacy::class.java)
        val mockRect = mock<Rect>()
        testedManager.addTouchOverrideArea(mockRect, fakePrivacy)
        assertThat(testedManager.nextOverrideSnapshot[mockRect]).isEqualTo(fakePrivacy)

        // When
        testedManager.copyNextSnapshotToCurrentSnapshot()

        // Then
        assertThat(testedManager.currentOverrideSnapshot[mockRect]).isEqualTo(fakePrivacy)
        assertThat(testedManager.nextOverrideSnapshot).isEmpty()
    }

    @Test
    fun `M resolve privacy override W resolveTouchPrivacy() { within override area }`(
        forge: Forge
    ) {
        // Given
        testedManager = TouchPrivacyManager(TouchPrivacy.HIDE)
        val fakePrivacy = TouchPrivacy.SHOW
        val fakePoint = Point(
            forge.aPositiveInt(),
            forge.aPositiveInt()
        )

        val fakeRect = Rect(
            fakePoint.x - forge.aPositiveInt(),
            fakePoint.y - forge.aPositiveInt(),
            fakePoint.x + forge.aPositiveInt(),
            fakePoint.y + forge.aPositiveInt()
        )

        testedManager.addTouchOverrideArea(fakeRect, fakePrivacy)
        testedManager.copyNextSnapshotToCurrentSnapshot()

        // When
        val resolvedPrivacy = testedManager.resolveTouchPrivacy(fakePoint)

        // Then
        assertThat(resolvedPrivacy).isEqualTo(TouchPrivacy.SHOW)
    }

    @Test
    fun `M resolve global privacy W resolveTouchPrivacy() { outside override area }`(
        forge: Forge
    ) {
        // Given
        testedManager = TouchPrivacyManager(TouchPrivacy.SHOW)
        val fakePrivacy = TouchPrivacy.HIDE
        val fakePoint = Point(
            forge.aPositiveInt(),
            forge.aPositiveInt()
        )

        val fakeRect = Rect(
            fakePoint.x + forge.aPositiveInt(),
            fakePoint.y + forge.aPositiveInt(),
            fakePoint.x + forge.aPositiveInt() * 2,
            fakePoint.y + forge.aPositiveInt() * 2
        )

        testedManager.addTouchOverrideArea(fakeRect, fakePrivacy)
        testedManager.copyNextSnapshotToCurrentSnapshot()

        // When
        val resolvedPrivacy = testedManager.resolveTouchPrivacy(fakePoint)

        // Then
        assertThat(resolvedPrivacy).isEqualTo(TouchPrivacy.SHOW)
    }

    @Test
    fun `M resolve as HIDE W resolveTouchPrivacy { matches both HIDE and SHOW }`(
        forge: Forge
    ) {
        // Given
        val fakePoint = Point(
            forge.aPositiveInt(),
            forge.aPositiveInt()
        )

        val fakeHiddenArea = Rect(
            fakePoint.x - forge.aPositiveInt(),
            fakePoint.y - forge.aPositiveInt(),
            fakePoint.x + forge.aPositiveInt(),
            fakePoint.y + forge.aPositiveInt()
        )

        val fakeShownArea = Rect(
            fakePoint.x - forge.aPositiveInt(),
            fakePoint.y - forge.aPositiveInt(),
            fakePoint.x + forge.aPositiveInt(),
            fakePoint.y + forge.aPositiveInt()
        )

        testedManager.addTouchOverrideArea(fakeHiddenArea, TouchPrivacy.HIDE)
        testedManager.addTouchOverrideArea(fakeShownArea, TouchPrivacy.SHOW)
        testedManager.copyNextSnapshotToCurrentSnapshot()

        // When
        val resolvedPrivacy = testedManager.resolveTouchPrivacy(fakePoint)

        // Then
        assertThat(resolvedPrivacy).isEqualTo(TouchPrivacy.HIDE)
    }
}
