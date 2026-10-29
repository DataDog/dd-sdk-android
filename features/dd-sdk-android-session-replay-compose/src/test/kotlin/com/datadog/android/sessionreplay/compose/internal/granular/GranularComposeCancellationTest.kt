/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.view.View
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class GranularComposeCancellationTest {
    private val mockSemanticsUtils: SemanticsUtils = mock()
    private val mockReflectionUtils: ReflectionUtils = mock()
    private val mockHostView: View = mock()
    private val testedGate = GranularComposeCompatibilityGate()
    private val testedDecomposer = GranularComposeDecomposer(
        semanticsUtils = mockSemanticsUtils,
        reflectionUtils = mockReflectionUtils,
        compatibilityGate = testedGate
    )

    @ParameterizedTest
    @EnumSource(CancellationPoint::class)
    fun `M discard partial output and allow later captures W decompose() { capture cancelled }`(
        fakeCancellationPoint: CancellationPoint,
        forge: Forge
    ) {
        // Given
        var fakeShouldContinue = fakeCancellationPoint != CancellationPoint.BEFORE_START
        val fakeBounds = if (fakeCancellationPoint == CancellationPoint.CLIPPED_SIBLINGS) {
            GlobalBounds(10, 110, 20, 20)
        } else {
            GlobalBounds(10, 10, 20, 20)
        }
        val fakeFirst = node(forge, fakeBounds)
        val fakeSecond = node(forge, fakeBounds)
        val fakeRoots = when (fakeCancellationPoint) {
            CancellationPoint.NESTED_CHILDREN -> listOf(
                node(forge, fakeBounds, listOf(fakeFirst, fakeSecond)),
                node(forge, fakeBounds)
            )
            CancellationPoint.CLIPPED_SIBLINGS -> listOf(fakeFirst, fakeSecond) + List(30) {
                node(forge, fakeBounds)
            }
            else -> listOf(fakeFirst, fakeSecond)
        }
        val stubRoot = node(forge, fakeBounds, fakeRoots)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockHostView)).thenReturn(stubRoot)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(fakeFirst), any())).thenAnswer {
            fakeShouldContinue = false
            fakeBounds
        }
        val stubIdentityFactory: CompositionIdentityFactory = mock()
        whenever(stubIdentityFactory.composeNode(any(), any())).thenAnswer { mock<CapturedIdentity>() }
        val fakeRequest = CompositionHostDecomposeRequest(
            identityFactory = stubIdentityFactory,
            hostIdentity = mock(),
            screenDensity = 1f,
            hostBounds = CapturedBounds(0, 0, 100, 100),
            textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL,
            internalLogger = mock(),
            shouldContinue = { fakeShouldContinue },
            nativeViewHandoff = { _, _ -> false }
        )

        // When
        val fakeCancelledResult = testedDecomposer.decompose(mockHostView, fakeRequest)

        // Then
        assertThat(fakeCancelledResult).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
        verifyNoInteractions(fakeSecond)
        if (fakeCancellationPoint == CancellationPoint.BEFORE_START) {
            verifyNoInteractions(mockSemanticsUtils, mockReflectionUtils)
        }

        // Given
        fakeShouldContinue = true
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(fakeFirst), any())).thenReturn(fakeBounds)

        // When
        val fakeNextResult = testedDecomposer.decompose(mockHostView, fakeRequest)

        // Then
        assertThat(fakeNextResult).isNotNull()
        assertThat(testedGate.isAvailable()).isTrue()
    }

    private fun node(
        forge: Forge,
        bounds: GlobalBounds,
        children: List<SemanticsNode> = emptyList()
    ): SemanticsNode {
        val stubLayoutInfo: LayoutInfo = mock {
            on { getModifierInfo() } doReturn emptyList()
            on { isAttached } doReturn true
            on { isPlaced } doReturn true
        }
        val fakeId = forge.anInt()
        val node: SemanticsNode = mock {
            on { id } doReturn fakeId
            on { this.children } doReturn children
            on { layoutInfo } doReturn stubLayoutInfo
        }
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(bounds)
        whenever(mockSemanticsUtils.resolveBackgroundColor(node)).thenReturn(null)
        return node
    }

    enum class CancellationPoint {
        BEFORE_START,
        BETWEEN_SIBLINGS,
        NESTED_CHILDREN,
        CLIPPED_SIBLINGS
    }
}
