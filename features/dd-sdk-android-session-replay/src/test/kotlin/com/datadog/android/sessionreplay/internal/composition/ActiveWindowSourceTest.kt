/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import com.datadog.android.sessionreplay.TouchPrivacy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock

internal class ActiveWindowSourceTest {

    @Test
    fun `M deny touches W window awaits privacy publication`() {
        // Given
        val fixture = Fixture()

        // When
        val areas = fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)

        // Then
        assertThat(areas).isNull()
    }

    @Test
    fun `M expose window overrides W privacy is published`() {
        // Given
        val fixture = Fixture()

        // When
        fixture.publish()

        // Then
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
    }

    @Test
    fun `M deny touches W RUM view changes`() {
        // Given
        val fixture = Fixture()
        fixture.publish()
        val fakeNextScope = RumViewIdentityScope("next")

        // When
        val areas = fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fakeNextScope)

        // Then
        assertThat(areas).isNull()
    }

    @Test
    fun `M deny touches W window is removed and rediscovered`() {
        // Given
        val fixture = Fixture()
        fixture.publish()

        // When
        fixture.testedSource.update(emptyList())
        fixture.testedSource.update(listOf(fixture.mockRoot))

        // Then
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)).isNull()
    }

    @Test
    fun `M retain gesture scope W animation redraws with unchanged touch policy`() {
        // Given
        val fixture = Fixture()
        fixture.publish()
        val fakeInitialScope = fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)

        // When
        val scopesAfterRedraw = List(3) {
            fixture.testedSource.onDraw(fixture.mockRoot)
            val scope = fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)
            fixture.publish()
            scope
        }

        // Then
        assertThat(scopesAfterRedraw).containsOnly(fakeInitialScope)
        assertThat(fixture.fakeReadCount).isEqualTo(3)
    }

    @Test
    fun `M change gesture scope W redraw changes touch policy before visual capture`() {
        // Given
        val fixture = Fixture()
        fixture.publish()
        val fakeInitialScope = fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)
        fixture.fakeAreas = mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)

        // When
        fixture.testedSource.onDraw(fixture.mockRoot)

        // Then
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope))
            .isNotEqualTo(fakeInitialScope)
    }

    @Test
    fun `M deny touches W dirty privacy refresh fails`() {
        // Given
        val fixture = Fixture()
        fixture.publish()
        fixture.fakeReadAllowed = false

        // When
        fixture.testedSource.onDraw(fixture.mockRoot)

        // Then
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)).isNull()
        assertThat(fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject stale live read W redraw or window removal overtakes refresh`(fakeRemove: Boolean) {
        // Given
        val fixture = Fixture()
        fixture.publish()
        fixture.testedSource.onDraw(fixture.mockRoot)
        fixture.onRead = {
            if (fakeRemove) fixture.testedSource.clear() else fixture.testedSource.onDraw(fixture.mockRoot)
        }

        // When
        val result = fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M refresh stale candidate W draw overtakes publication`() {
        // Given
        val fixture = Fixture()
        val fakeSnapshot = fixture.testedSource.snapshot()
        fixture.testedSource.onDraw(fixture.mockRoot)
        var fakePublished = false

        // When
        fixture.testedSource.publishTouchPrivacy(
            fakeSnapshot,
            listOf(fixture.mockRoot),
            fixture.fakeScope,
            mapOf(fixture.mockRoot to fixture.fakeAreas)
        ) { fakePublished = true }
        fixture.fakeAreas = mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)

        // Then
        assertThat(fakePublished).isFalse()
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.fakeReadCount).isEqualTo(1)
    }

    @Test
    fun `M isolate touch policies W windows overlap`() {
        // Given
        val fixture = Fixture()
        val mockOtherRoot: View = mock()
        val fakeHiddenAreas = mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)
        fixture.testedSource.update(listOf(fixture.mockRoot, mockOtherRoot))

        // When
        fixture.testedSource.publishTouchPrivacy(
            fixture.testedSource.snapshot(),
            listOf(fixture.mockRoot, mockOtherRoot),
            fixture.fakeScope,
            mapOf(fixture.mockRoot to fixture.fakeAreas, mockOtherRoot to fakeHiddenAreas)
        ) {}

        // Then
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.testedSource.currentTouchOverrideAreas(mockOtherRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fakeHiddenAreas)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `M refresh only the drawing window W another window has a clean privacy policy`(fakeDrawnWindowIndex: Int) {
        // Given
        val fixture = Fixture()
        val mockOtherRoot: View = mock()
        val fakeRoots = listOf(fixture.mockRoot, mockOtherRoot)
        val fakeInitialAreas = mapOf(
            fixture.mockRoot to fixture.fakeAreas,
            mockOtherRoot to mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)
        )
        fixture.testedSource.update(fakeRoots)
        fixture.testedSource.publishTouchPrivacy(
            fixture.testedSource.snapshot(),
            fakeRoots,
            fixture.fakeScope,
            fakeInitialAreas
        ) {}
        val mockDrawnRoot = fakeRoots[fakeDrawnWindowIndex]
        val mockUnchangedRoot = fakeRoots[1 - fakeDrawnWindowIndex]

        // When
        fixture.testedSource.onDraw(mockDrawnRoot)
        val unchangedAreas = fixture.testedSource.currentTouchOverrideAreas(mockUnchangedRoot, fixture.fakeScope)
        val readsAfterUnchangedWindow = fixture.fakeReadCount
        val refreshedAreas = fixture.testedSource.currentTouchOverrideAreas(mockDrawnRoot, fixture.fakeScope)

        // Then
        assertThat(unchangedAreas).containsExactlyEntriesOf(fakeInitialAreas.getValue(mockUnchangedRoot))
        assertThat(readsAfterUnchangedWindow).isZero()
        assertThat(refreshedAreas).containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.fakeReadCount).isEqualTo(1)
    }

    private class Fixture {
        val mockRoot: View = mock()
        val fakeScope = RumViewIdentityScope("view")
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
