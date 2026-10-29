/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedTruncationMode
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class), ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class GranularComposeContentTest : GranularComposeTestFixture() {
    @Test
    fun `M retain text backgrounds and descendants W decompose()`(@StringForgery fakeText: String) {
        // Given
        val child = node(bounds = GlobalBounds(40, 50, 10, 10))
        val parent = node(listOf(child))
        scene(parent)
        text(parent, fakeText)
        whenever(mockSemanticsUtils.resolveBackgroundColor(parent)).thenReturn(Color.Red.value.toLong())

        // When
        val result = testedDecomposer.decompose(mockHost, request(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS))

        // Then
        val captured = checkNotNull(result)
        assertThat(captured.nodes).hasSize(2)
        val textLayer = captured.nodes.single { it.bounds == CapturedBounds(10, 20, 30, 40) }
        assertThat(textLayer.children).hasSize(3)
        val shape = captured.wireframes.filterIsInstance<CapturedWireframe.Shape>().single()
        val text = captured.wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(
            textLayer.children
        ).startsWith(CapturedChild.Wireframe(shape.identity), CapturedChild.Wireframe(text.identity))
        assertThat(text.text).isEqualTo(fakeText)
        assertThat(textLayer.children.last()).isInstanceOf(CapturedChild.Layer::class.java)
    }

    @ParameterizedTest
    @CsvSource("false,true", "true,false", "false,false")
    fun `M skip unavailable geometry before resolving bounds W decompose()`(
        fakeAttached: Boolean,
        fakePlaced: Boolean
    ) {
        // Given
        val child = node()
        scene(child)
        whenever(child.layoutInfo.isAttached).thenReturn(fakeAttached)
        whenever(child.layoutInfo.isPlaced).thenReturn(fakePlaced)

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils, never()).resolveInnerBounds(eq(child), any())
    }

    @Test
    fun `M retain placed content at origin W decompose()`() {
        // Given
        val child = node(bounds = GlobalBounds(0, 0, 50, 20))
        whenever(child.positionInRoot).thenReturn(Offset.Zero)
        scene(child)

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        val captured = checkNotNull(result)
        assertThat(captured.nodes.single().bounds).isEqualTo(CapturedBounds(0, 0, 50, 20))
    }

    @Test
    fun `M resolve a direct AndroidComposeView host W canDecompose() + decompose()`() {
        // Given
        val root = scene(node())
        val stubOwner: SemanticsOwner = mock()
        whenever(stubOwner.unmergedRootSemanticsNode).thenReturn(root)
        whenever(mockReflectionUtils.isAndroidComposeView(mockHost)).thenReturn(true)
        whenever(mockReflectionUtils.getSemanticsOwner(mockHost)).thenReturn(stubOwner)

        // When
        val recognized = testedDecomposer.canDecompose(mockHost)
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        assertThat(recognized).isTrue()
        assertThat(result).isNotNull()
        verify(mockSemanticsUtils, never()).findRootSemanticsNode(mockHost)
    }

    @Test
    fun `M preserve every static opacity W decompose()`() {
        // Given
        val child = node()
        scene(child)
        val modifiers = listOf(0.5f, 1f, 0.5f).map { alpha ->
            val modifier: ModifierInfo = mock()
            whenever(modifier.modifier).thenReturn(FakeGraphicsLayer(alpha))
            modifier
        }
        whenever(child.layoutInfo.getModifierInfo()).thenReturn(modifiers)

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        val captured = checkNotNull(result)
        assertThat(captured.nodes.single().modifiers)
            .containsExactly(CapturedModifier.Opacity(0.5), CapturedModifier.Opacity(0.5))
    }

    @ParameterizedTest
    @CsvSource("LEFT,CLIP", "CENTER,TAIL", "RIGHT,HEAD", "RIGHT,MIDDLE")
    fun `M preserve resolved text style W decompose()`(
        fakeAlignment: CapturedHorizontalAlignment,
        fakeTruncation: CapturedTruncationMode,
        @StringForgery fakeText: String
    ) {
        // Given
        val child = node()
        scene(child)
        val initial = text(child, fakeText)
        val layout = initial.copy(
            fontFamily = FontFamily.Serif,
            textAlign = when (fakeAlignment) {
                CapturedHorizontalAlignment.LEFT -> TextAlign.Left
                CapturedHorizontalAlignment.CENTER -> TextAlign.Center
                CapturedHorizontalAlignment.RIGHT -> TextAlign.Right
            },
            textOverflow = MobileSegment.TruncationMode.valueOf(fakeTruncation.name)
        )
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(child), any())).thenReturn(layout)

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        val captured = checkNotNull(result).wireframes.filterIsInstance<CapturedWireframe.Text>().single()
        assertThat(captured.textStyle.family).isEqualTo("serif")
        assertThat(captured.textStyle.size).isEqualTo(layout.fontSize)
        assertThat(captured.textStyle.truncationMode).isEqualTo(fakeTruncation)
        assertThat(captured.textPosition?.alignment?.horizontal).isEqualTo(fakeAlignment)
    }

    @Test
    fun `M report handled reflection warnings through request logger W decompose()`(
        @StringForgery fakeMessage: String
    ) {
        // Given
        val child = node()
        scene(child)
        val layout = text(child, fakeMessage)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(child), any())).thenAnswer {
            val logger = it.getArgument<InternalLogger>(1)
            logger.log(InternalLogger.Level.WARN, InternalLogger.Target.TELEMETRY, { fakeMessage }, onlyOnce = true)
            layout
        }

        // When
        val result = testedDecomposer.decompose(mockHost, request())

        // Then
        assertThat(result).isNotNull()
        mockLogger.verifyLog(InternalLogger.Level.WARN, InternalLogger.Target.TELEMETRY, fakeMessage, onlyOnce = true)
        assertThat(testedGate.isAvailable()).isTrue()
    }

    private class FakeGraphicsLayer(private val alpha: Float) : Modifier.Element, InspectableValue {
        override val nameFallback: String = "graphicsLayer"
        override val valueOverride: Any? = null
        override val inspectableElements: Sequence<ValueElement> get() = sequenceOf(ValueElement("alpha", alpha))
    }
}
