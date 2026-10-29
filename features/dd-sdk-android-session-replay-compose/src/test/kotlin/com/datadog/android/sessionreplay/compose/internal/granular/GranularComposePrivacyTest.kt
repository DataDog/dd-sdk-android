/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsOwner
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import androidx.compose.ui.geometry.Rect as ComposeRect

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class GranularComposePrivacyTest : GranularComposeTestFixture() {
    @ParameterizedTest
    @CsvSource(
        "MASK_ALL,false",
        "MASK_ALL,true",
        "MASK_ALL_INPUTS,false",
        "MASK_ALL_INPUTS,true",
        "MASK_SENSITIVE_INPUTS,false",
        "MASK_SENSITIVE_INPUTS,true"
    )
    fun `M apply effective text privacy W decompose()`(
        fakePrivacy: TextAndInputPrivacy,
        fakeInput: Boolean,
        @StringForgery(regex = "[a-zA-Z]{16}") fakeText: String
    ) {
        // Given
        val child = node(input = fakeInput)
        scene(child)
        text(child, fakeText)
        val expected = if (fakeInput && fakePrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
            "***"
        } else if (fakePrivacy == TextAndInputPrivacy.MASK_ALL) {
            StringObfuscator.getStringObfuscator().obfuscate(fakeText)
        } else {
            fakeText
        }

        // When
        val result = testedDecomposer.decompose(mockHost, request(fakePrivacy))

        // Then
        val captured = checkNotNull(result).wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(captured.text).isEqualTo(expected)
        if (fakePrivacy == TextAndInputPrivacy.MASK_ALL ||
            (fakeInput && fakePrivacy == TextAndInputPrivacy.MASK_ALL_INPUTS)
        ) {
            assertThat(captured.text).doesNotContain(fakeText)
        }
    }

    @ParameterizedTest
    @CsvSource("false,MASK_ALL", "true,MASK_ALL", "false,MASK_SENSITIVE_INPUTS", "true,MASK_SENSITIVE_INPUTS")
    fun `M inherit Compose overrides without affecting siblings W decompose()`(
        fakeRootOverride: Boolean,
        fakePrivacy: TextAndInputPrivacy,
        @StringForgery(regex = "[a-zA-Z]{16}") fakeText: String
    ) {
        // Given
        val descendant = node()
        val sibling = node()
        val parent = node(listOf(descendant))
        val root = scene(parent, sibling)
        text(descendant, fakeText)
        text(sibling, fakeText)
        whenever(mockSemanticsUtils.getTextAndInputPrivacyOverride(if (fakeRootOverride) root else parent))
            .thenReturn(fakePrivacy)
        val globalPrivacy = if (fakePrivacy == TextAndInputPrivacy.MASK_ALL) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        } else {
            TextAndInputPrivacy.MASK_ALL
        }
        val masked = StringObfuscator.getStringObfuscator().obfuscate(fakeText)
        val inherited = if (fakePrivacy == TextAndInputPrivacy.MASK_ALL) masked else fakeText
        val siblingText = if (fakeRootOverride) {
            inherited
        } else if (globalPrivacy ==
            TextAndInputPrivacy.MASK_ALL
        ) {
            masked
        } else {
            fakeText
        }

        // When
        val result = testedDecomposer.decompose(mockHost, request(globalPrivacy))

        // Then
        val texts = checkNotNull(result).wireframes.filterIsInstance<CapturedWireframe.Text>().map { it.text }
        assertThat(texts).containsExactly(inherited, siblingText)
    }

    @ParameterizedTest
    @EnumSource(TouchPrivacy::class)
    fun `M return touch overrides in screen pixels W decompose()`(fakePrivacy: TouchPrivacy) {
        // Given
        val child = node()
        scene(child)
        whenever(child.boundsInRoot).thenReturn(ComposeRect(10.25f, 20.75f, 40.25f, 60.75f))
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(child)).thenReturn(fakePrivacy)
        doAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = 100
            location[1] = 200
            null
        }.whenever(mockHost).getLocationOnScreen(any())

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        val captured = checkNotNull(result)
        assertThat(captured.touchOverrideAreas).containsExactlyEntriesOf(mapOf(Rect(110, 220, 141, 261) to fakePrivacy))
    }

    @ParameterizedTest
    @CsvSource("false,false", "true,false", "false,true", "true,true")
    fun `M retain HIDE precedence including visually hidden descendants W decompose()`(
        fakeReverseOrder: Boolean,
        fakeHidden: Boolean
    ) {
        // Given
        val hide = node()
        val show = node()
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(hide)).thenReturn(TouchPrivacy.HIDE)
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(show)).thenReturn(TouchPrivacy.SHOW)
        val parent = node(if (fakeReverseOrder) listOf(show, hide) else listOf(hide, show))
        scene(parent)
        whenever(mockSemanticsUtils.isNodeHidden(parent)).thenReturn(fakeHidden)

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        val captured = checkNotNull(result)
        assertThat(
            captured.touchOverrideAreas
        ).containsExactlyEntriesOf(mapOf(Rect(10, 20, 40, 60) to TouchPrivacy.HIDE))
        if (fakeHidden) {
            assertThat(
                captured.wireframes.single()
            ).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        }
    }

    @Test
    fun `M refresh Compose touch overrides without visual mapping W readTouchOverrideAreas()`() {
        // Given
        val child = node()
        val root = scene(child)
        val owner: SemanticsOwner = mock()
        whenever(owner.unmergedRootSemanticsNode).thenReturn(root)
        whenever(mockReflectionUtils.isAndroidComposeView(mockHost)).thenReturn(true)
        whenever(mockReflectionUtils.getSemanticsOwner(mockHost)).thenReturn(owner)
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(child)).thenReturn(TouchPrivacy.HIDE)

        // When
        val result = testedDecomposer.readTouchOverrideAreas(mockHost) { true }

        // Then
        assertThat(result).containsEntry(Rect(10, 20, 40, 60), TouchPrivacy.HIDE)
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(child), any())
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard partial touch refresh W readTouchOverrideAreas() { cancellation }`(fakeAlreadyCancelled: Boolean) {
        // Given
        val first = node()
        val second = node()
        val root = scene(first, second)
        val owner: SemanticsOwner = mock()
        whenever(owner.unmergedRootSemanticsNode).thenReturn(root)
        whenever(mockReflectionUtils.isAndroidComposeView(mockHost)).thenReturn(true)
        whenever(mockReflectionUtils.getSemanticsOwner(mockHost)).thenReturn(owner)
        var fakeContinue = !fakeAlreadyCancelled
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(first)).thenAnswer {
            fakeContinue = false
            TouchPrivacy.HIDE
        }

        // When
        val result = testedDecomposer.readTouchOverrideAreas(mockHost) { fakeContinue }

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils, never()).getTouchPrivacyOverride(second)
    }

    @Test
    fun `M deny touch refresh after incompatibility W readTouchOverrideAreas()`() {
        // Given
        whenever(mockReflectionUtils.isAndroidComposeView(mockHost)).thenReturn(true)
        testedGate.markIncompatible()

        // When
        val result = testedDecomposer.readTouchOverrideAreas(mockHost) { true }

        // Then
        assertThat(result).isNull()
        verify(mockReflectionUtils, never()).getSemanticsOwner(mockHost)
    }

    @Test
    fun `M retain touch overrides for large offscreen sibling groups W decompose()`() {
        // Given
        val children = List(32) { node(bounds = GlobalBounds(10, 1100, 20, 20)) }
        val first = children.first()
        whenever(first.boundsInRoot).thenReturn(ComposeRect(20f, 2200f, 60f, 2240f))
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(first)).thenReturn(TouchPrivacy.HIDE)
        scene(*children.toTypedArray())

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        assertThat(checkNotNull(result).touchOverrideAreas).containsEntry(Rect(20, 2200, 60, 2240), TouchPrivacy.HIDE)
    }
}
