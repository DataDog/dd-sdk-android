/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.mapper.AllowAllWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewScreenshotWireframeMapper
import com.datadog.android.sessionreplay.internal.utils.copy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness
import java.util.LinkedList

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotProducerTest {

    lateinit var testedSnapshotProducer: SnapshotProducer

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Mock
    lateinit var mockViewScreenshotWireframeMapper: ViewScreenshotWireframeMapper

    @Mock
    lateinit var mockGenericWireframeMapper: AllowAllWireframeMapper

    @Forgery
    lateinit var fakeViewWireframes: List<MobileSegment.Wireframe>

    @Forgery
    lateinit var fakeShapeScreenShotWireframes: List<MobileSegment.Wireframe.ShapeWireframe>

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @BeforeEach
    fun `set up`(forge: Forge) {
        // making sure the root has shapeStyle
        fakeViewWireframes = forge.aList {
            getForgery<MobileSegment.Wireframe>().copy(shapeStyle = getForgery())
        }
        fakeShapeScreenShotWireframes = forge.aList {
            getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(shapeStyle = getForgery())
        }
        whenever(mockViewScreenshotWireframeMapper.map(any(), eq(fakeSystemInformation)))
            .thenReturn(fakeShapeScreenShotWireframes)
        whenever(mockGenericWireframeMapper.map(any(), eq(fakeSystemInformation)))
            .thenReturn(fakeViewWireframes)
        whenever(mockGenericWireframeMapper.imageMapper)
            .thenReturn(mockViewScreenshotWireframeMapper)
        whenever(mockViewUtilsInternal.checkIfNotVisible(any())).thenReturn(false)
        whenever(mockViewUtilsInternal.checkIfSystemNoise(any())).thenReturn(false)
        testedSnapshotProducer = SnapshotProducer(
            mockGenericWireframeMapper,
            mockViewUtilsInternal
        )
    }

    // region Default Tests

    @Test
    fun `M produce a tree of Nodes W produce() { single view }`(forge: Forge) {
        // Given
        val fakeRoot = forge.aMockView<View>()

        // When
        val snapshot = testedSnapshotProducer.produce(fakeRoot, fakeSystemInformation)

        // Then
        val expectedSnapshot = fakeRoot.toNode(viewMappedWireframes = fakeViewWireframes)
        assertThat(snapshot).isEqualTo(expectedSnapshot).usingRecursiveComparison()
    }

    @Test
    fun `M produce a tree of Nodes W produce() { view tree }`(forge: Forge) {
        // Given
        val fakeRoot = forge.aMockViewWithChildren(2, 0, 2)

        // When
        val snapshot = testedSnapshotProducer.produce(fakeRoot, fakeSystemInformation)

        // Then
        val expectedSnapshot = fakeRoot.toNode(viewMappedWireframes = fakeViewWireframes)
        assertThat(snapshot).isEqualTo(expectedSnapshot).usingRecursiveComparison()
    }

    // endregion

    // region visibility tests

    @Test
    fun `M return null W produce() { any view is not visible }`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aMockViewWithChildren(2, 0, 2)
            .apply { whenever(mockViewUtilsInternal.checkIfNotVisible(this)).thenReturn(true) }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakeSystemInformation)).isNull()
    }

    // endregion

    // region System Noise

    @Test
    fun `M return null W produce() { any view is system noise }`(forge: Forge) {
        // Given
        val fakeRoot = forge
            .aMockViewWithChildren(2, 0, 2)
            .apply { whenever(mockViewUtilsInternal.checkIfSystemNoise(this)).thenReturn(true) }

        // Then
        assertThat(testedSnapshotProducer.produce(fakeRoot, fakeSystemInformation)).isNull()
    }

    // endregion

    // region Toolbar

    @Test
    fun `M resolve a Node with screenshot with border W produce() { view is Toolbar }`(
        forge: Forge
    ) {
        // Given
        val mockToolBar: Toolbar = forge.aMockView<Toolbar>().apply {
            whenever(mockViewUtilsInternal.checkIsToolbar(this)).thenReturn(true)
        }

        // When
        val snapshot = testedSnapshotProducer.produce(mockToolBar, fakeSystemInformation)

        // Then
        val shapeWireframes = snapshot?.wireframes
        assertThat(shapeWireframes).isEqualTo(fakeShapeScreenShotWireframes)
    }

    // endregion

    // region Internals

    private fun View.toNode(
        level: Int = 0,
        viewMappedWireframes: List<MobileSegment.Wireframe>
    ): Node {
        val children = if (this is ViewGroup) {
            val nodes = mutableListOf<Node>()
            for (i in 0 until childCount) {
                nodes.add(this.getChildAt(i).toNode(level + 1, viewMappedWireframes))
            }
            nodes
        } else {
            emptyList()
        }
        val parents = LinkedList<MobileSegment.Wireframe>()
        repeat(level) {
            parents += viewMappedWireframes
        }
        return Node(
            wireframes = viewMappedWireframes,
            children = children,
            parents = parents
        )
    }

    // endregion
}
