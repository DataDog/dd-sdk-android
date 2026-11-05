/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import androidx.compose.ui.platform.ComposeView
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidWindowTraversalCompositionTest : AndroidWindowTraversalTestFixture() {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M hand off typed Compose host without visiting native children W traverseWindow`(
        fakeReturnsWireframes: Boolean,
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeRootBounds)
        val mockComposeChild = mockView(fakeRootBounds)
        whenever(mockComposeHost.childCount).thenReturn(1)
        whenever(mockComposeHost.getChildAt(0)).thenReturn(mockComposeChild)
        val fakeMapping = if (fakeReturnsWireframes) {
            CapturedViewMapperResult.Wireframes(emptyList())
        } else {
            CapturedViewMapperResult.None
        }
        val mockComposeMapper: CapturedViewMapper<ComposeView> = mock()
        whenever(mockComposeMapper.map(eq(mockComposeHost), any())).thenReturn(fakeMapping)
        val fakeInteropCallback = CapturedInteropViewCallback { view, context ->
            (view as? ComposeView)?.let { mockComposeMapper.map(it, context) }
        }

        // When
        val result = traversal(interopViewCallback = fakeInteropCallback).traverseWindow(
            mockComposeHost,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        verify(mockComposeMapper).map(eq(mockComposeHost), isA())
        verify(mockComposeHost, never()).getChildAt(any())
        assertThat((result as WindowCopyResult.Present).window.views).hasSize(1)
    }

    @Test
    fun `M hand off non-Compose host W traverseWindow { integration recognizes view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockChild = mockView(fakeBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val fakeInteropCallback = CapturedInteropViewCallback { view, _ ->
            if (view === mockRoot) CapturedViewMapperResult.None else null
        }

        // When
        val result = traversal(interopViewCallback = fakeInteropCallback).traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        assertThat((result as WindowCopyResult.Present).window.views).hasSize(1)
        verify(mockRoot, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockChild)
    }

    @Test
    fun `M retain native mapping and children W traverseWindow { integration declines view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockChild = mockView(fakeBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val mockInteropCallback: CapturedInteropViewCallback = mock()
        val testedTraversal = traversal(fallback = markerMapper, interopViewCallback = mockInteropCallback)

        // When
        val result = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        verify(mockInteropCallback).tryMap(eq(mockRoot), isA())
        verify(mockInteropCallback).tryMap(eq(mockChild), isA())
        assertThat(result.layers).hasSize(2)
        assertThat(result.wireframes).hasSize(2)
    }

    @Test
    fun `M retain hidden placeholder W traverseWindow { interop callback supplied }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeBounds)
        whenever(mockComposeHost.getTag(R.id.datadog_hidden)).thenReturn(true)
        val mockInteropCallback: CapturedInteropViewCallback = mock()

        // When
        val result = traversal(interopViewCallback = mockInteropCallback).traverseWindow(
            mockComposeHost,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        verifyNoInteractions(mockInteropCallback)
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M abort W traverseWindow() { deadline expires during compose host handoff, before next checkpoint tick }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 1000L) fakeDeadlineNs: Long
    ) {
        // Given
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeRootBounds)
        val fakeWindowIdentity = identityFactory.window("window")
        var fakeCalls = 0
        val fakeExpiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = fakeDeadlineNs,
            timeProvider = CaptureTimeProvider {
                fakeCalls++
                if (fakeCalls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            interopViewCallback = CapturedInteropViewCallback { view, _ ->
                if (view is ComposeView) CapturedViewMapperResult.None else null
            }
        )

        // When
        val result = testedTraversal.traverseWindow(
            mockComposeHost,
            fakeWindowIdentity,
            identityFactory,
            fakeExpiringContext
        )

        // Then
        assertThat(result.doneValue()).isEqualTo(WindowWalkResult.Aborted)
    }
}
