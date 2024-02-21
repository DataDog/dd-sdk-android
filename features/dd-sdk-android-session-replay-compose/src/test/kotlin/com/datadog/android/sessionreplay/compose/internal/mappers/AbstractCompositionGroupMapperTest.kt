/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.unit.IntSize
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.data.ComposeContext
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class AbstractCompositionGroupMapperTest {

    private lateinit var testedMapper: StubAbstractCompositionGroupMapper

    @Forgery
    private lateinit var fakeComposeContext: ComposeContext

    @Forgery
    private lateinit var fakeUiContext: UiContext

    @Forgery
    private lateinit var fakeWireframe: ComposeWireframe

    @LongForgery
    private var fakeGroupId: Long = 0L

    @BeforeEach
    fun `set up`() {
        testedMapper = StubAbstractCompositionGroupMapper()
    }

    @Test
    fun `M return null W map() {group without coordinates}`() {
        // Given
        testedMapper.mappedWireframe = fakeWireframe
        val mockGroup = mockGroupWithoutCoordinates()

        // When
        val result = testedMapper.map(mockGroup, fakeComposeContext, fakeUiContext)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return implementation result W map() {group with coordinates}`(
        forge: Forge
    ) {
        // Given
        testedMapper.mappedWireframe = fakeWireframe
        testedMapper.expectedGroupId = fakeGroupId.hashCode().toLong()
        val mockGroup = mockGroupWithCoordinates(forge)

        // When
        val result = testedMapper.map(mockGroup, fakeComposeContext, fakeUiContext)

        // Then
        assertThat(result).isSameAs(fakeWireframe)
    }

    private fun mockGroupWithoutCoordinates(): CompositionGroup {
        return mock<CompositionGroup?>().apply {
            whenever(identity) doReturn null
            whenever(key) doReturn fakeGroupId
        }
    }

    private fun mockGroupWithCoordinates(
        forge: Forge
    ): CompositionGroup {
        val fakeOffset = Offset.Zero.copy(forge.aFloat(), forge.aFloat())
        val fakeSize = IntSize(forge.anInt(), forge.anInt())
        val mockCoordinates = org.mockito.kotlin.mock<LayoutCoordinates>()
        whenever(mockCoordinates.localToWindow(Offset.Zero)) doReturn fakeOffset
        whenever(mockCoordinates.size) doReturn fakeSize
        val mockLayoutInfo = org.mockito.kotlin.mock<LayoutInfo>()
        whenever(mockLayoutInfo.isAttached) doReturn true
        whenever(mockLayoutInfo.coordinates) doReturn mockCoordinates
        val mockGroup = org.mockito.kotlin.mock<CompositionGroup>()
        whenever(mockGroup.node) doReturn mockLayoutInfo
        whenever(mockGroup.identity) doReturn null
        whenever(mockGroup.key) doReturn fakeGroupId

        return mockGroup
    }
}

internal class StubAbstractCompositionGroupMapper : AbstractCompositionGroupMapper() {

    var mappedWireframe: ComposeWireframe? = null

    var expectedGroupId: Long = 0
    var expectedComposeContext: ComposeContext? = null
    var expectedUIContext: UiContext? = null

    override fun map(
        stableGroupId: Long,
        parameters: Sequence<ComposableParameter>,
        boxWithDensity: Box,
        uiContext: UiContext
    ): ComposeWireframe? {
        if (stableGroupId == expectedGroupId) {
            return mappedWireframe
        } else {
            return null
        }
    }
}
