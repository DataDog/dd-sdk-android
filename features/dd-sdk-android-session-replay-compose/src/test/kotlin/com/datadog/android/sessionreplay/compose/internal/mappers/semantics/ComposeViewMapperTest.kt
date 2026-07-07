/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
class ComposeViewMapperTest {

    @Mock
    private lateinit var mockRootSemanticsNodeMapper: RootSemanticsNodeMapper

    @Mock
    private lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    private lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    private lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    private lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    private lateinit var mockView: ComposeView

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockSemanticsUtils: SemanticsUtils

    @Mock
    private lateinit var mockSemanticsConfiguration: SemanticsConfiguration

    @Mock
    private lateinit var mockLayoutInfo: LayoutInfo

    @Forgery
    private lateinit var fakeMappingContext: MappingContext

    private lateinit var testedComposeViewMapper: ComposeViewMapper

    @BeforeEach
    fun `set up`() {
        testedComposeViewMapper = ComposeViewMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper,
            mockSemanticsUtils,
            mockRootSemanticsNodeMapper
        )
        // Node density 0f falls back to system density, matching the tests below that assert
        // against fakeMappingContext.systemInformation.screenDensity.
        whenever(mockLayoutInfo.density) doReturn Density(0f)
        whenever(mockRootSemanticsNodeMapper.createComposeWireframes(any(), any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        val defaultRootSemanticsNode = mockSemanticsNode(null)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(defaultRootSemanticsNode)
    }

    @Test
    fun `M invoke rootSemanticsNodeMapper createComposeWireframes W map`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(mockSemanticsNode)

        // When
        testedComposeViewMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockRootSemanticsNodeMapper).createComposeWireframes(
            eq(mockSemanticsNode),
            eq(fakeMappingContext.systemInformation.screenDensity),
            eq(fakeMappingContext),
            eq(mockAsyncJobStatusCallback),
            any(),
            any()
        )
    }

    @Test
    fun `M return empty list W map {no root semantics node}`() {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(null)

        // When
        val result = testedComposeViewMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(result).isEmpty()
        verify(mockView, never()).getLocationOnScreen(any())
    }

    @Test
    fun `M pass window offset to createComposeWireframes W map {view has non-zero screen position}`(forge: Forge) {
        // Given
        // windowOffset is now forwarded to RootSemanticsNodeMapper and baked into each wireframe's
        // bounds at creation time (see SemanticsUtils.resolveInnerBounds), instead of being applied
        // as a post-hoc translation here. Post-hoc translation would silently detach async-populated
        // fields like ImageWireframe.resourceId from the wireframe actually returned. See RUM-16362.
        val fakeScreenX = forge.anInt(min = 1, max = 1000)
        val fakeScreenY = forge.anInt(min = 1, max = 1000)
        val density = fakeMappingContext.systemInformation.screenDensity.let { if (it == 0.0f) 1.0f else it }
        doAnswer { invocation ->
            val array = invocation.arguments[0] as IntArray
            array[0] = fakeScreenX
            array[1] = fakeScreenY
            null
        }.whenever(mockView).getLocationOnScreen(any())

        // When
        testedComposeViewMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        val expectedWindowOffset = ComposeWindowOffset(
            xPx = fakeScreenX,
            yPx = fakeScreenY,
            xDp = (fakeScreenX / density).toLong(),
            yDp = (fakeScreenY / density).toLong()
        )
        verify(mockRootSemanticsNodeMapper).createComposeWireframes(
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(expectedWindowOffset)
        )
    }

    @Test
    fun `M use root semantics node density for window offset W map {node overrides density}`(forge: Forge) {
        // Given
        val fakeScreenX = forge.anInt(min = 1, max = 1000)
        val fakeScreenY = forge.anInt(min = 1, max = 1000)
        val fakeNodeDensity = forge.aFloat(min = 0.5f, max = 4f)
        whenever(mockLayoutInfo.density) doReturn Density(fakeNodeDensity)
        doAnswer { invocation ->
            val array = invocation.arguments[0] as IntArray
            array[0] = fakeScreenX
            array[1] = fakeScreenY
            null
        }.whenever(mockView).getLocationOnScreen(any())

        // When
        testedComposeViewMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then — offset and the density passed down both use the root node's own density
        val expectedWindowOffset = ComposeWindowOffset(
            xPx = fakeScreenX,
            yPx = fakeScreenY,
            xDp = (fakeScreenX / fakeNodeDensity).toLong(),
            yDp = (fakeScreenY / fakeNodeDensity).toLong()
        )
        verify(mockRootSemanticsNodeMapper).createComposeWireframes(
            any(),
            eq(fakeNodeDensity),
            any(),
            any(),
            any(),
            eq(expectedWindowOffset)
        )
    }

    private fun mockSemanticsNode(role: Role?): SemanticsNode {
        return mock {
            whenever(mockSemanticsConfiguration.getOrNull(SemanticsProperties.Role)) doReturn role
            whenever(it.config) doReturn mockSemanticsConfiguration
            whenever(it.layoutInfo) doReturn mockLayoutInfo
        }
    }
}
