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
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList

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

    @Test
    fun `M not throw W updateCurrentTouchOverrideAreas() { concurrent addTouchOverrideArea }`(
        forge: Forge
    ) {
        // Given
        // Each recorded window runs the snapshot pipeline on the Looper thread its view
        // hierarchy is attached to, so a traversal writing overrides can run concurrently
        // with the copy-and-clear swap performed at the end of another window's snapshot.
        val fakePrivacyOverride = forge.aValueFrom(TouchPrivacy::class.java)
        val fakeOrigin = forge.anInt(min = 0, max = 1000)
        val iterations = 10_000
        val errors = CopyOnWriteArrayList<Throwable>()

        // When
        listOf(
            Thread {
                repeat(iterations) {
                    try {
                        testedManager.addTouchOverrideArea(
                            Rect(fakeOrigin + it, fakeOrigin + it, fakeOrigin + it + 1, fakeOrigin + it + 1),
                            fakePrivacyOverride
                        )
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            },
            Thread {
                repeat(iterations) {
                    try {
                        testedManager.updateCurrentTouchOverrideAreas()
                        testedManager.shouldRecordTouch(Point(fakeOrigin + it, fakeOrigin + it))
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        ).shuffled(Random(forge.seed))
            .map { it.apply { start() } }
            .forEach { it.join() }

        // Then
        assertThat(errors).isEmpty()
    }

    @Test
    fun `M never publish a partial mix W concurrent windows rebuild and swap`(forge: Forge) {
        // Given
        // In production, one WindowsOnDrawListener instance is shared across every recorded
        // window. Each window's onDraw pass rebuilds the FULL override set (across all windows)
        // on its own thread, then swaps it into currentOverrideAreas once. Two windows' passes
        // running concurrently must never leave currentOverrideAreas holding a mix of one
        // pass's entries and another's partially-built entries -- only ever one pass's complete,
        // self-consistent set.
        val privacyA = forge.aValueFrom(TouchPrivacy::class.java)
        val privacyB = forge.aValueFrom(TouchPrivacy::class.java)
        val rangeSize = forge.anInt(min = 10, max = 30)
        val originA = forge.anInt(min = 0, max = 1000)
        val originB = originA + rangeSize + forge.anInt(min = 50, max = 200)
        val setA = (originA until originA + rangeSize).associate { Rect(it, it, it + 1, it + 1) to privacyA }
        val setB = (originB until originB + rangeSize).associate { Rect(it, it, it + 1, it + 1) to privacyB }
        val iterations = 2_000
        val errors = CopyOnWriteArrayList<Throwable>()
        val invalidSnapshots = CopyOnWriteArrayList<Map<Rect, TouchPrivacy>>()

        fun runOnePass(overrides: Map<Rect, TouchPrivacy>) {
            overrides.forEach { (rect, privacy) -> testedManager.addTouchOverrideArea(rect, privacy) }
            testedManager.updateCurrentTouchOverrideAreas()
            val snapshot = testedManager.getCurrentOverrideAreas()
            if (snapshot != setA && snapshot != setB) {
                invalidSnapshots.add(snapshot)
            }
        }

        // When
        listOf(
            Thread { repeat(iterations) { try { runOnePass(setA) } catch (e: Throwable) { errors.add(e) } } },
            Thread { repeat(iterations) { try { runOnePass(setB) } catch (e: Throwable) { errors.add(e) } } }
        ).shuffled(Random(forge.seed))
            .map { it.apply { start() } }
            .forEach { it.join() }

        // Then
        assertThat(errors).isEmpty()
        assertThat(invalidSnapshots)
            .describedAs("currentOverrideAreas must always equal one pass's complete set, never a partial mix")
            .isEmpty()
    }
}
