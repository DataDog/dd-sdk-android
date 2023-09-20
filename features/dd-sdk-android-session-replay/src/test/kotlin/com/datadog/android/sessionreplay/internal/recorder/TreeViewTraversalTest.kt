/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.TextView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class TreeViewTraversalTest {

    private lateinit var testedTreeViewTraversal: TreeViewTraversal

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Mock
    lateinit var mockViewMapper: ViewWireframeMapper

    @Mock
    lateinit var mockDecorViewMapper: DecorViewMapper

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @Mock
    lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs

    @BeforeEach
    fun `set up`() {
        whenever(mockViewUtilsInternal.isNotVisible(any())).thenReturn(false)
        whenever(mockViewUtilsInternal.isSystemNoise(any())).thenReturn(false)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal
        )
    }

    // region traverse and mapper

    @Test
    fun `M stop traversing W traverse { mapper for view type provided }`(forge: Forge) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe> = forge.aList { getForgery() }
        val mockViews: List<View> = listOf(
            forge.aMockView<RadioButton>(),
            forge.aMockView<CompoundButton>(),
            forge.aMockView<CheckedTextView>(),
            forge.aMockView<Button>(),
            forge.aMockView<TextView>()
        )
        val fakeTypes: List<Class<*>> = mockViews.map { it::class.java }
        val fakeTypeToMapperMap: Map<Class<*>, WireframeMapper<View, *>> = fakeTypes
            .associateWith { mock() }
        val fakeTypeMapperWrappers = fakeTypes.map {
            val mapper = fakeTypeToMapperMap[it]!!
            MapperTypeWrapper(it, mapper)
        }
        val mockView = forge.anElementFrom(mockViews)
        whenever(
            fakeTypeToMapperMap[mockView::class.java]!!.map(
                eq(mockView),
                eq(fakeMappingContext),
                any()
            )
        )
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            fakeTypeMapperWrappers,
            mockViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal
        )

        // When
        val traversedTreeView = testedTreeViewTraversal.traverse(
            mockView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(traversedTreeView.mappedWireframes).isEqualTo(fakeViewMappedWireframes)
        assertThat(traversedTreeView.nextActionStrategy)
            .isEqualTo(TreeViewTraversal.TraversalStrategy.STOP_AND_RETURN_NODE)
    }

    @Test
    fun `M default to view mapper and continue W traverse { mapper for view type not provided }`(
        forge: Forge
    ) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe.ShapeWireframe> =
            forge.aList { getForgery() }
        val mockViews: List<View> = listOf(
            forge.aMockView<RadioButton>(),
            forge.aMockView<CheckedTextView>(),
            forge.aMockView<Button>(),
            forge.aMockView<CompoundButton>(),
            forge.aMockView<TextView>()
        )
        val mockView = forge.anElementFrom(mockViews).apply {
            whenever(this.parent)
                .thenReturn(mock<ViewGroup>())
        }
        whenever(mockViewMapper.map(eq(mockView), eq(fakeMappingContext), any()))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal
        )

        // When
        val traversedTreeView = testedTreeViewTraversal.traverse(
            mockView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(traversedTreeView.mappedWireframes).isEqualTo(fakeViewMappedWireframes)
        assertThat(traversedTreeView.nextActionStrategy)
            .isEqualTo(TreeViewTraversal.TraversalStrategy.TRAVERSE_ALL_CHILDREN)
    }

    @Test
    fun `M use the decor view mapper and continue W traverse { view with no View type parent }`(
        forge: Forge
    ) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe.ShapeWireframe> =
            forge.aList { getForgery() }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.parent).thenReturn(mock())
        }
        whenever(mockDecorViewMapper.map(eq(mockView), eq(fakeMappingContext), any()))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal
        )

        // When
        val traversedTreeView = testedTreeViewTraversal.traverse(
            mockView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(traversedTreeView.mappedWireframes).isEqualTo(fakeViewMappedWireframes)
        assertThat(traversedTreeView.nextActionStrategy)
            .isEqualTo(TreeViewTraversal.TraversalStrategy.TRAVERSE_ALL_CHILDREN)
    }

    @Test
    fun `M use the decor view mapper and continue W traverse { view has no parent }`(
        forge: Forge
    ) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe.ShapeWireframe> =
            forge.aList { getForgery() }
        val mockView = forge.aMockView<View>().apply {
            whenever(this.parent).thenReturn(null)
        }

        whenever(mockDecorViewMapper.map(eq(mockView), eq(fakeMappingContext), any()))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal
        )

        // When
        val traversedTreeView = testedTreeViewTraversal.traverse(
            mockView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(traversedTreeView.mappedWireframes).isEqualTo(fakeViewMappedWireframes)
        assertThat(traversedTreeView.nextActionStrategy)
            .isEqualTo(TreeViewTraversal.TraversalStrategy.TRAVERSE_ALL_CHILDREN)
    }

    // endregion

    // region visibility tests

    @Test
    fun `M return STOP_AND_DROP_NODE W traverse(){ any view is not visible }`(forge: Forge) {
        // Given
        val fakeRoot = forge.aMockView<View>().apply {
            whenever(mockViewUtilsInternal.isNotVisible(this)).thenReturn(true)
        }

        // When
        val traversedTreeView = testedTreeViewTraversal.traverse(
            fakeRoot,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(traversedTreeView.mappedWireframes).isEmpty()
        assertThat(traversedTreeView.nextActionStrategy)
            .isEqualTo(TreeViewTraversal.TraversalStrategy.STOP_AND_DROP_NODE)
    }

    // endregion

    // region System Noise

    @Test
    fun `M return STOP_AND_DROP_NODE W traverse(){ any view is system noise }`(forge: Forge) {
        // Given
        val fakeRoot = forge.aMockView<View>().apply {
            whenever(mockViewUtilsInternal.isSystemNoise(this)).thenReturn(true)
        }

        // When
        val traversedTreeView = testedTreeViewTraversal.traverse(
            fakeRoot,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(traversedTreeView.mappedWireframes).isEmpty()
        assertThat(traversedTreeView.nextActionStrategy)
            .isEqualTo(TreeViewTraversal.TraversalStrategy.STOP_AND_DROP_NODE)
    }

    // endregion
}
