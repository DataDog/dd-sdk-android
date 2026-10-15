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
    fun `M deny touches W window awaits publication or RUM view changes`() {
        val fixture = Fixture()
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)).isNull()

        fixture.publish()

        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, RumViewIdentityScope("next")))
            .isNull()
        fixture.testedSource.update(emptyList())
        fixture.testedSource.update(listOf(fixture.mockRoot))
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)).isNull()
    }

    @Test
    fun `M retain gesture scope W animation redraws with unchanged touch policy`() {
        val fixture = Fixture()
        fixture.publish()
        val fakeInitialScope = fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)

        repeat(3) {
            fixture.testedSource.onDraw(listOf(fixture.mockRoot))
            assertThat(fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope))
                .isEqualTo(fakeInitialScope)
            fixture.publish()
        }

        assertThat(fixture.fakeReadCount).isEqualTo(3)
    }

    @Test
    fun `M change gesture scope W redraw changes touch policy before visual capture`() {
        val fixture = Fixture()
        fixture.publish()
        val fakeInitialScope = fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)
        fixture.fakeAreas = mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)

        fixture.testedSource.onDraw(listOf(fixture.mockRoot))

        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope))
            .isNotEqualTo(fakeInitialScope)
    }

    @Test
    fun `M deny touches W dirty privacy refresh fails`() {
        val fixture = Fixture()
        fixture.publish()
        fixture.fakeReadAllowed = false

        fixture.testedSource.onDraw(listOf(fixture.mockRoot))

        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)).isNull()
        assertThat(fixture.testedSource.currentTouchPrivacyScope(fixture.mockRoot, fixture.fakeScope)).isNull()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reject stale live read W redraw or window removal overtakes refresh`(fakeRemove: Boolean) {
        val fixture = Fixture()
        fixture.publish()
        fixture.testedSource.onDraw(listOf(fixture.mockRoot))
        fixture.onRead = {
            if (fakeRemove) fixture.testedSource.clear() else fixture.testedSource.onDraw(listOf(fixture.mockRoot))
        }

        val result = fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope)

        assertThat(result).isNull()
    }

    @Test
    fun `M refresh stale candidate W draw overtakes publication`() {
        val fixture = Fixture()
        val fakeSnapshot = fixture.testedSource.snapshot()
        fixture.testedSource.onDraw(listOf(fixture.mockRoot))
        var fakePublished = false

        fixture.testedSource.publishTouchPrivacy(
            fakeSnapshot,
            listOf(fixture.mockRoot),
            fixture.fakeScope,
            mapOf(fixture.mockRoot to fixture.fakeAreas)
        ) { fakePublished = true }
        fixture.fakeAreas = mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)

        assertThat(fakePublished).isFalse()
        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.fakeReadCount).isEqualTo(1)
    }

    @Test
    fun `M isolate touch policies W windows overlap`() {
        val fixture = Fixture()
        val mockOtherRoot: View = mock()
        val fakeHiddenAreas = mapOf(fixture.fakeBounds to TouchPrivacy.HIDE)
        fixture.testedSource.update(listOf(fixture.mockRoot, mockOtherRoot))

        fixture.testedSource.publishTouchPrivacy(
            fixture.testedSource.snapshot(),
            listOf(fixture.mockRoot, mockOtherRoot),
            fixture.fakeScope,
            mapOf(fixture.mockRoot to fixture.fakeAreas, mockOtherRoot to fakeHiddenAreas)
        ) {}

        assertThat(fixture.testedSource.currentTouchOverrideAreas(fixture.mockRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fixture.fakeAreas)
        assertThat(fixture.testedSource.currentTouchOverrideAreas(mockOtherRoot, fixture.fakeScope))
            .containsExactlyEntriesOf(fakeHiddenAreas)
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
