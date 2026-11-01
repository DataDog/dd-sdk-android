/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ActiveWindowSourceTest {

    @StringForgery
    lateinit var fakeViewId: String

    @Test
    fun `M deny touches W currentTouchOverrideAreas() { unpublished window }`() {
        // Given
        val fakeFixture = Fixture()

        // When
        val result = fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return published areas W currentTouchOverrideAreas() { current RUM view }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()

        // When
        val result = fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope)

        // Then
        assertThat(result).containsExactlyEntriesOf(fakeFixture.fakeAreas)
    }

    @Test
    fun `M deny touches W currentTouchOverrideAreas() { different RUM view }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()
        val fakeOtherScope = RumViewIdentityScope(fakeViewId + "-next")

        // When
        val result = fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeOtherScope)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M deny touches W currentTouchOverrideAreas() { window removed and re-added }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()
        fakeFixture.testedSource.update(emptyList())
        fakeFixture.testedSource.update(listOf(fakeFixture.mockRoot))

        // When
        val result = fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope)

        // Then
        assertThat(result).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `M retain gesture scope W currentTouchPrivacyScope() { unchanged policy after redraw }`(fakePriorFrames: Int) {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()
        val fakeInitialScope = fakeFixture.testedSource.currentTouchPrivacyScope(
            fakeFixture.mockRoot,
            fakeFixture.fakeScope
        )
        repeat(fakePriorFrames) {
            fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)
            fakeFixture.testedSource.currentTouchPrivacyScope(fakeFixture.mockRoot, fakeFixture.fakeScope)
            fakeFixture.publish()
        }
        fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)

        // When
        val result = fakeFixture.testedSource.currentTouchPrivacyScope(fakeFixture.mockRoot, fakeFixture.fakeScope)

        // Then
        assertThat(result).isEqualTo(fakeInitialScope)
    }

    @Test
    fun `M read live privacy once per draw W currentTouchPrivacyScope() { repeated unchanged redraws }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()

        // When
        repeat(3) {
            fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)
            fakeFixture.testedSource.currentTouchPrivacyScope(fakeFixture.mockRoot, fakeFixture.fakeScope)
            fakeFixture.publish()
        }

        // Then
        assertThat(fakeFixture.fakeReadCount).isEqualTo(3)
    }

    @Test
    fun `M change gesture scope W onDraw() { redraw changes touch policy before visual capture }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()
        val fakeInitialScope = fakeFixture.testedSource.currentTouchPrivacyScope(
            fakeFixture.mockRoot,
            fakeFixture.fakeScope
        )
        fakeFixture.fakeAreas = mapOf(fakeFixture.fakeBounds to TouchPrivacy.HIDE)

        // When
        fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)

        // Then
        assertThat(fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope))
            .containsExactlyEntriesOf(fakeFixture.fakeAreas)
        assertThat(fakeFixture.testedSource.currentTouchPrivacyScope(fakeFixture.mockRoot, fakeFixture.fakeScope))
            .isNotEqualTo(fakeInitialScope)
    }

    @Test
    fun `M deny touches W onDraw() { privacy refresh fails }`() {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()
        fakeFixture.fakeReadAllowed = false

        // When
        fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)

        // Then
        assertThat(
            fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope)
        ).isNull()
        assertThat(
            fakeFixture.testedSource.currentTouchPrivacyScope(fakeFixture.mockRoot, fakeFixture.fakeScope)
        ).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject stale live read W currentTouchOverrideAreas() { redraw or window removal overtakes refresh }`(
        fakeRemove: Boolean
    ) {
        // Given
        val fakeFixture = Fixture()
        fakeFixture.publish()
        fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)
        fakeFixture.onRead = {
            if (fakeRemove) {
                fakeFixture.testedSource.clear()
            } else {
                fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)
            }
        }

        // When
        val result = fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M reject stale candidate W publishTouchPrivacy() { draw overtakes publication }`() {
        // Given
        val fakeFixture = Fixture()
        val fakeSnapshot = fakeFixture.testedSource.snapshot()
        fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)
        var fakePublished = false

        // When
        fakeFixture.testedSource.publishTouchPrivacy(
            fakeSnapshot,
            listOf(fakeFixture.mockRoot),
            fakeFixture.fakeScope,
            mapOf(fakeFixture.mockRoot to fakeFixture.fakeAreas)
        ) { fakePublished = true }

        // Then
        assertThat(fakePublished).isFalse()
    }

    @Test
    fun `M refresh live privacy W currentTouchOverrideAreas() { stale publication rejected }`() {
        // Given
        val fakeFixture = Fixture()
        val fakeSnapshot = fakeFixture.testedSource.snapshot()
        fakeFixture.testedSource.onDraw(fakeFixture.mockRoot)
        fakeFixture.testedSource.publishTouchPrivacy(
            fakeSnapshot,
            listOf(fakeFixture.mockRoot),
            fakeFixture.fakeScope,
            mapOf(fakeFixture.mockRoot to fakeFixture.fakeAreas)
        ) {}
        fakeFixture.fakeAreas = mapOf(fakeFixture.fakeBounds to TouchPrivacy.HIDE)

        // When
        val result = fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope)

        // Then
        assertThat(result).containsExactlyEntriesOf(fakeFixture.fakeAreas)
        assertThat(fakeFixture.fakeReadCount).isEqualTo(1)
    }

    @Test
    fun `M isolate touch policies W publishTouchPrivacy() { windows overlap }`() {
        // Given
        val fakeFixture = Fixture()
        val mockOtherRoot: View = mock()
        val fakeHiddenAreas = mapOf(fakeFixture.fakeBounds to TouchPrivacy.HIDE)
        fakeFixture.testedSource.update(listOf(fakeFixture.mockRoot, mockOtherRoot))

        // When
        fakeFixture.testedSource.publishTouchPrivacy(
            fakeFixture.testedSource.snapshot(),
            listOf(fakeFixture.mockRoot, mockOtherRoot),
            fakeFixture.fakeScope,
            mapOf(fakeFixture.mockRoot to fakeFixture.fakeAreas, mockOtherRoot to fakeHiddenAreas)
        ) {}

        // Then
        assertThat(fakeFixture.testedSource.currentTouchOverrideAreas(fakeFixture.mockRoot, fakeFixture.fakeScope))
            .containsExactlyEntriesOf(fakeFixture.fakeAreas)
        assertThat(fakeFixture.testedSource.currentTouchOverrideAreas(mockOtherRoot, fakeFixture.fakeScope))
            .containsExactlyEntriesOf(fakeHiddenAreas)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `M refresh only the drawing window W onDraw() { another window has a clean privacy policy }`(
        fakeDrawnWindowIndex: Int
    ) {
        // Given
        val fakeFixture = Fixture()
        val mockOtherRoot: View = mock()
        val fakeRoots = listOf(fakeFixture.mockRoot, mockOtherRoot)
        val fakeInitialAreas = mapOf(
            fakeFixture.mockRoot to fakeFixture.fakeAreas,
            mockOtherRoot to mapOf(fakeFixture.fakeBounds to TouchPrivacy.HIDE)
        )
        fakeFixture.testedSource.update(fakeRoots)
        fakeFixture.testedSource.publishTouchPrivacy(
            fakeFixture.testedSource.snapshot(),
            fakeRoots,
            fakeFixture.fakeScope,
            fakeInitialAreas
        ) {}
        val mockDrawnRoot = fakeRoots[fakeDrawnWindowIndex]
        val mockUnchangedRoot = fakeRoots[1 - fakeDrawnWindowIndex]

        // When
        fakeFixture.testedSource.onDraw(mockDrawnRoot)
        val fakeUnchangedAreas = fakeFixture.testedSource.currentTouchOverrideAreas(
            mockUnchangedRoot,
            fakeFixture.fakeScope
        )
        val fakeReadsAfterUnchangedWindow = fakeFixture.fakeReadCount
        val fakeRefreshedAreas = fakeFixture.testedSource.currentTouchOverrideAreas(
            mockDrawnRoot,
            fakeFixture.fakeScope
        )

        // Then
        assertThat(fakeUnchangedAreas).containsExactlyEntriesOf(fakeInitialAreas.getValue(mockUnchangedRoot))
        assertThat(fakeReadsAfterUnchangedWindow).isZero()
        assertThat(fakeRefreshedAreas).containsExactlyEntriesOf(fakeFixture.fakeAreas)
        assertThat(fakeFixture.fakeReadCount).isEqualTo(1)
    }

    private inner class Fixture {
        val mockRoot: View = mock()
        val fakeScope = RumViewIdentityScope(fakeViewId)
        val fakeBounds = Rect(0, 0, 100, 100)
        var fakeAreas = mapOf(fakeBounds to TouchPrivacy.SHOW)
        var fakeReadAllowed = true
        var fakeReadCount = 0
        var onRead: () -> Unit = {}
        val testedSource = ActiveWindowSource {
            fakeReadCount++
            onRead()
            fakeAreas.takeIf { fakeReadAllowed }
        }.apply { update(listOf(mockRoot)) }

        fun publish() {
            testedSource.publishTouchPrivacy(
                testedSource.snapshot(),
                listOf(mockRoot),
                fakeScope,
                mapOf(mockRoot to fakeAreas)
            ) {}
        }
    }
}
