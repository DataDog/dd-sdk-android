/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    private lateinit var mockSemanticsConfiguration: SemanticsConfiguration

    @Forgery
    private lateinit var fakeMappingContext: MappingContext

    @Forgery
    private lateinit var fakePrivacy: SessionReplayPrivacy

    private lateinit var testedRootSemanticsNodeMapper: RootSemanticsNodeMapper

    @BeforeEach
    fun `set up`() {
        testedRootSemanticsNodeMapper = RootSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils,
            semanticsNodeMapper = mapOf(
                Role.RadioButton to mockRadioButtonSemanticsNodeMapper,
                Role.Tab to mockTabSemanticsNodeMapper,
                Role.Button to mockButtonSemanticsNodeMapper,
                Role.Image to mockImageSemanticsNodeMapper
            ),
            textSemanticsNodeMapper = mockTextSemanticsNodeMapper,
            containerSemanticsNodeMapper = mockContainerSemanticsNodeMapper
        )
    }

    @Test
    fun `M use ContainerSemanticsNodeMapper W map { role is missing }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            fakePrivacy,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockContainerSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use ButtonSemanticsNodeMapper W map { role is Button }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Button)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            fakePrivacy,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockButtonSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use RadioButtonSemanticsNodeMapper W map { role is RadioButton }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.RadioButton)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            fakePrivacy,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockRadioButtonSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use TabSemanticsNodeMapper W map { role is Tab }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Tab)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            fakePrivacy,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockTabSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M use ImageSemanticsNodeMapper W map { role is Image }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(Role.Image)

        // When
        testedRootSemanticsNodeMapper.createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            fakePrivacy,
            mockAsyncJobStatusCallback
        )

        // Then
        verify(mockImageSemanticsNodeMapper).map(
            eq(mockSemanticsNode),
            any(),
            eq(mockAsyncJobStatusCallback)
        )
    }

    private fun mockSemanticsNode(role: Role?): SemanticsNode {
        return mock {
            whenever(mockSemanticsConfiguration.getOrNull(SemanticsProperties.Role)) doReturn role
            whenever(it.config) doReturn mockSemanticsConfiguration
        }
    }
}
