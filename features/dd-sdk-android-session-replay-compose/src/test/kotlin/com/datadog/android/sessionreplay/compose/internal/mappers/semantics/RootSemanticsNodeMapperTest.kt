/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.graphics.toRect
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.ComposeWindowOffset
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
class RootSemanticsNodeMapperTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockContainerSemanticsNodeMapper: ContainerSemanticsNodeMapper

    @Mock
    private lateinit var mockTextSemanticsNodeMapper: TextSemanticsNodeMapper

    @Mock
    private lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockSemanticsUtils: SemanticsUtils

    @Mock
    private lateinit var mockRadioButtonSemanticsNodeMapper: RadioButtonSemanticsNodeMapper

    @Mock
    private lateinit var mockTabSemanticsNodeMapper: TabSemanticsNodeMapper

    @Mock
    private lateinit var mockButtonSemanticsNodeMapper: ButtonSemanticsNodeMapper

    @Mock
    private lateinit var mockImageSemanticsNodeMapper: ImageSemanticsNodeMapper

    @Mock
    private lateinit var mockCheckboxSemanticsNodeMapper: CheckboxSemanticsNodeMapper

    @Mock
    private lateinit var mockSwitchSemanticsNodeMapper: SwitchSemanticsNodeMapper

    @Mock
    private lateinit var mockComposeHiddenMapper: ComposeHiddenMapper

    @Mock
    private lateinit var mockSliderSemanticsNodeMapper: SliderSemanticsNodeMapper

    @Forgery
    private lateinit var fakeMappingContext: MappingContext

    private lateinit var testedRootSemanticsNodeMapper: RootSemanticsNodeMapper

    private lateinit var rolesToMappers: Map<Role, SemanticsNodeMapper>

    @BeforeEach
    fun `set up`() {
        rolesToMappers = mapOf(
            Role.RadioButton to mockRadioButtonSemanticsNodeMapper,
            Role.Tab to mockTabSemanticsNodeMapper,
            Role.Button to mockButtonSemanticsNodeMapper,
            Role.Image to mockImageSemanticsNodeMapper,
            Role.Checkbox to mockCheckboxSemanticsNodeMapper,
            Role.Switch to mockSwitchSemanticsNodeMapper,
            Role.DropdownList to mockContainerSemanticsNodeMapper
        )

        testedRootSemanticsNodeMapper = RootSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils,
            semanticsNodeMapper = rolesToMappers,
            textSemanticsNodeMapper = mockTextSemanticsNodeMapper,
            containerSemanticsNodeMapper = mockContainerSemanticsNodeMapper,
            composeHiddenMapper = mockComposeHiddenMapper,
            sliderSemanticsNodeMapper = mockSliderSemanticsNodeMapper
        )
    }

    @Test
    fun `M use ContainerSemanticsNodeMapper W createComposeWireframes { role is missing }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockContainerSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M thread window offset into UiContext W createComposeWireframes`(forge: Forge) {
        // Given — windowOffset must reach child mappers via UiContext so bounds are baked in at
        // wireframe-creation time (not translated after the fact, which would detach async-mutated
        // fields like ImageWireframe.resourceId from the wireframe instance actually returned).
        // See RUM-16362.
        val mockSemanticsNode = mockSemanticsNode(null)
        val fakeWindowOffset = ComposeWindowOffset(
            xPx = forge.anInt(min = 1, max = 500),
            yPx = forge.anInt(min = 1, max = 500),
            xDp = forge.aLong(min = 1, max = 500),
            yDp = forge.aLong(min = 1, max = 500)
        )

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger,
            windowOffset = fakeWindowOffset
        )

        // Then
        val contextCaptor = argumentCaptor<UiContext>()
        verify(mockContainerSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = contextCaptor.capture(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
        assertThat(contextCaptor.firstValue.windowOffset).isEqualTo(fakeWindowOffset)
    }

    @Test
    fun `M use ButtonSemanticsNodeMapper W createComposeWireframes { role is Button }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Button)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockButtonSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use RadioButtonSemanticsNodeMapper W createComposeWireframes { role is RadioButton }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.RadioButton)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockRadioButtonSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use SwitchSemanticsNodeMapper W createComposeWireframes { role is Switch }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Switch)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockSwitchSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use TabSemanticsNodeMapper W createComposeWireframes { role is Tab }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Tab)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockTabSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use ImageSemanticsNodeMapper W createComposeWireframes { role is Image }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Image)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockImageSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use CheckboxSemanticsNodeMapper W createComposeWireframes { role is Checkbox }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Checkbox)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockCheckboxSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use SliderSemanticsNodeMapper W map createComposeWireframes { isSliderNode }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)
        val mockProgressBarRangeInfo = mock<ProgressBarRangeInfo>()
        whenever(mockSemanticsUtils.getProgressBarRangeInfo(mockSemanticsNode)) doReturn mockProgressBarRangeInfo

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockSliderSemanticsNodeMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M use ComposeHideMapper W node is hidden`(forge: Forge) {
        // Given
        val fakeRole = forge.anElementFrom(
            rolesToMappers.keys + null
        )
        val mockSemanticsNode = mockSemanticsNode(fakeRole)
        whenever(mockSemanticsUtils.isNodeHidden(mockSemanticsNode)) doReturn true

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockComposeHiddenMapper).map(
            semanticsNode = eq(mockSemanticsNode),
            parentContext = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            internalLogger = eq(mockInternalLogger)
        )
    }

    @Test
    fun `M skip semanticsNode W position unavailable`() {
        // Given
        rolesToMappers.forEach { (role, mapper) ->
            val node = mockSemanticsNode(role)

            whenever(mockSemanticsUtils.isNodePositionUnavailable(node)).thenReturn(true)

            // When
            testedRootSemanticsNodeMapper.createComposeWireframes(
                node,
                fakeMappingContext.systemInformation.screenDensity,
                fakeMappingContext,
                mockAsyncJobStatusCallback,
                mockInternalLogger
            )

            // Then
            verifyNoInteractions(mapper)
        }
    }

    @Test
    fun `M call interop callback W semantics node has interop view`(forge: Forge) {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)
        val mockView = mock<View>()
        val fakeInteropWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = forge.aLong(),
            x = forge.aLong(min = 0, max = 500),
            y = forge.aLong(min = 0, max = 500),
            width = forge.aLong(min = 1, max = 500),
            height = forge.aLong(min = 1, max = 500)
        )
        whenever(mockSemanticsUtils.getInteropView(mockSemanticsNode)) doReturn mockView
        whenever(fakeMappingContext.interopViewCallback.map(mockView, fakeMappingContext))
            .thenReturn(listOf(fakeInteropWireframe))

        // When
        val result = testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(fakeMappingContext.interopViewCallback).map(
            eq(mockView),
            eq(fakeMappingContext)
        )
        assertThat(result).containsExactly(fakeInteropWireframe)
    }

    @Test
    fun `M preserve document order W createComposeWireframes {compose and interop siblings mixed}`(forge: Forge) {
        // Given — a Button child then an interop child; interop must not be pushed to the end.
        val parentNode = mockSemanticsNode(null)
        val composeChild = mockSemanticsNode(Role.Button)
        val interopChild = mockSemanticsNode(null)
        whenever(parentNode.children) doReturn listOf(composeChild, interopChild)

        val fakeComposeWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = forge.aLong(),
            x = forge.aLong(min = 0, max = 500),
            y = forge.aLong(min = 0, max = 500),
            width = forge.aLong(min = 1, max = 500),
            height = forge.aLong(min = 1, max = 500)
        )
        val fakeInteropWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = forge.aLong(),
            x = forge.aLong(min = 0, max = 500),
            y = forge.aLong(min = 0, max = 500),
            width = forge.aLong(min = 1, max = 500),
            height = forge.aLong(min = 1, max = 500)
        )
        val mockView = mock<View>()
        whenever(mockButtonSemanticsNodeMapper.map(eq(composeChild), any(), any(), any()))
            .thenReturn(SemanticsWireframe(listOf(fakeComposeWireframe), null))
        whenever(mockSemanticsUtils.getInteropView(interopChild)) doReturn mockView
        whenever(fakeMappingContext.interopViewCallback.map(mockView, fakeMappingContext))
            .thenReturn(listOf(fakeInteropWireframe))

        // When
        val result = testedRootSemanticsNodeMapper.createComposeWireframes(
            parentNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then — wireframes appear in traversal order: compose child before interop child
        assertThat(result).containsExactly(fakeComposeWireframe, fakeInteropWireframe)
    }

    @Test
    fun `M offset touch override area by window offset W createComposeWireframes {touch privacy override}`(
        forge: Forge
    ) {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)
        val fakeTouchPrivacy = forge.aValueFrom(TouchPrivacy::class.java)
        val fakeLeft = forge.aFloat(min = 0f, max = 100f)
        val fakeTop = forge.aFloat(min = 0f, max = 100f)
        val fakeBoundsInRoot = Rect(fakeLeft, fakeTop, fakeLeft + 50f, fakeTop + 50f)
        val fakeOffsetXPx = forge.anInt(min = 1, max = 500)
        val fakeOffsetYPx = forge.anInt(min = 1, max = 500)
        whenever(mockSemanticsUtils.getTouchPrivacyOverride(mockSemanticsNode)) doReturn fakeTouchPrivacy
        whenever(mockSemanticsNode.boundsInRoot) doReturn fakeBoundsInRoot

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger,
            windowOffset = ComposeWindowOffset(xPx = fakeOffsetXPx, yPx = fakeOffsetYPx, xDp = 0L, yDp = 0L)
        )

        // Then — captured rather than compared via eq() to avoid Rect's toString() misbehaving
        // inside Mockito's mismatch reporting under the Android unit-test stub jar.
        val areaCaptor = argumentCaptor<android.graphics.Rect>()
        verify(fakeMappingContext.touchPrivacyManager).addTouchOverrideArea(areaCaptor.capture(), eq(fakeTouchPrivacy))
        val expectedArea = fakeBoundsInRoot.toAndroidRectF().toRect().apply {
            offset(fakeOffsetXPx, fakeOffsetYPx)
        }
        assertThat(areaCaptor.firstValue.left).isEqualTo(expectedArea.left)
        assertThat(areaCaptor.firstValue.top).isEqualTo(expectedArea.top)
        assertThat(areaCaptor.firstValue.right).isEqualTo(expectedArea.right)
        assertThat(areaCaptor.firstValue.bottom).isEqualTo(expectedArea.bottom)
    }

    private fun mockSemanticsNode(role: Role?): SemanticsNode {
        // Each node gets its own config mock — sharing one across differently-roled siblings
        // would make the last stub win for all of them.
        val config = mock<SemanticsConfiguration>()
        whenever(config.getOrNull(SemanticsProperties.Role)) doReturn role
        return mock {
            whenever(it.config) doReturn config
        }
    }
}
