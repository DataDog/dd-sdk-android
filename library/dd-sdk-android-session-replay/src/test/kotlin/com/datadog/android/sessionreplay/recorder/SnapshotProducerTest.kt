/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.ActionBarContextView
import androidx.appcompat.widget.Toolbar
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.GenericWireframeMapper
import com.datadog.android.sessionreplay.recorder.mapper.ViewScreenshotWireframeMapper
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotProducerTest {

    lateinit var testedSnapshotProducer: SnapshotProducer

    @FloatForgery(min = 1f, max = 10f)
    var fakePixelDensity: Float = 1f

    @Mock
    lateinit var mockViewScreenshotWireframeMapper: ViewScreenshotWireframeMapper

    @Mock
    lateinit var mockGenericWireframeMapper: GenericWireframeMapper

    @Mock
    lateinit var mockViewWireframe: MobileSegment.Wireframe

    @Mock
    lateinit var mockShapeScreenshotWireframe: MobileSegment.Wireframe.ShapeWireframe

    @BeforeEach
    fun `set up`() {
        whenever(mockViewScreenshotWireframeMapper.map(any(), eq(fakePixelDensity)))
            .thenReturn(mockShapeScreenshotWireframe)
        whenever(mockGenericWireframeMapper.map(any(), eq(fakePixelDensity)))
            .thenReturn(mockViewWireframe)
        testedSnapshotProducer = SnapshotProducer(
            mockViewScreenshotWireframeMapper,
            mockGenericWireframeMapper
        )
    }

    // region Default Tests

    @Test
    fun `M produce a tree of Nodes W produce() { single view }`(forge: Forge) {
        // Given
        val fakeRoot = forge.aMockView<View>()

        // When
        val snapshot = testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)

        // Then
        val expectedSnapshot = fakeRoot.toNode()
        assertThat(snapshot).isEqualTo(expectedSnapshot).usingRecursiveComparison()
    }

    @Test
    fun `M produce a tree of Nodes W produce() { view tree }`(forge: Forge) {
        // Given
        val fakeRoot = forge.aViewWithChildren(2, 0, 2)

        // When
        val snapshot = testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)

        // Then
        val expectedSnapshot = fakeRoot.toNode()
        assertThat(snapshot).isEqualTo(expectedSnapshot).usingRecursiveComparison()
    }

    // endregion

    // region validity tests

    @Test
    fun `M return null W produce() { root is invisible }`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aViewWithChildren(2, 0, 2)
            .apply { whenever(this.isShown).thenReturn(false) }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    @Test
    fun `M return null W produce() { root width is 0 or less }`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aViewWithChildren(2, 0, 2)
            .apply {
                whenever(this.width).thenReturn(forge.anInt(max = 1))
            }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    @Test
    fun `M return null W produce() { root height is 0 or less }`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aViewWithChildren(2, 0, 2)
            .apply {
                whenever(this.height).thenReturn(forge.anInt(max = 1))
            }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    // endregion

    // region System Noise

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun `M exclude the navigationBarBackground W produce()`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aViewWithChildren(2, 0, 2)
            .apply {
                whenever(this.id).thenReturn(android.R.id.navigationBarBackground)
            }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun `M exclude the statusBarBackground W produce()`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aViewWithChildren(2, 0, 2)
            .apply {
                whenever(this.id).thenReturn(android.R.id.statusBarBackground)
            }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    @Test
    fun `M exclude the ViewStub W produce()`() {
        // Given
        val fakeRoot: ViewStub = mock()

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    @Test
    fun `M exclude the ActionBarContextView W produce()`() {
        // Given
        val fakeRoot: ActionBarContextView = mock()

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakePixelDensity)).isNull()
    }

    // endregion

    // region Toolbar

    @Test
    fun `M resolve a Node with screenshot with border W produce() { androidx Toolbar }`(
        forge: Forge
    ) {
        // Given
        val mockToolBar: Toolbar = forge.aMockView<Toolbar>().apply {
            whenever(this.id).thenReturn(androidx.appcompat.R.id.action_bar)
        }

        // When
        val snapshot = testedSnapshotProducer.produce(mockToolBar, fakePixelDensity)

        // Then
        val shapeWireframe = snapshot?.wireframes?.get(0) as MobileSegment.Wireframe.ShapeWireframe
        assertThat(shapeWireframe).isEqualTo(mockShapeScreenshotWireframe)
    }

    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Test
    fun `M resolve a Node with screenshot with border W produce() { android Toolbar }`(
        forge: Forge
    ) {
        // Given
        val mockToolBar: android.widget.Toolbar = forge.aMockView()

        // When
        val snapshot = testedSnapshotProducer.produce(mockToolBar, fakePixelDensity)

        // Then
        val shapeWireframe = snapshot?.wireframes?.get(0) as MobileSegment.Wireframe.ShapeWireframe
        assertThat(shapeWireframe).isEqualTo(mockShapeScreenshotWireframe)
    }

    // endregion

    // region Internals

    private fun View.toNode(): Node {
        val children = if (this is ViewGroup) {
            val nodes = mutableListOf<Node>()
            for (i in 0 until childCount) {
                nodes.add(this.getChildAt(i).toNode())
            }
            nodes
        } else emptyList()
        return Node(wireframes = listOf(mockViewWireframe), children = children)
    }

    // endregion
}
