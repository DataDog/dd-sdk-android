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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.TreeViewTraversal.Companion.METHOD_CALL_MAP_PREFIX
import com.datadog.android.sessionreplay.internal.recorder.mapper.DecorViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.TraverseAllChildrenMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class TreeViewTraversalTest {

    private lateinit var testedTreeViewTraversal: TreeViewTraversal

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Mock
    lateinit var mockDefaultViewMapper: ViewWireframeMapper

    @Mock
    lateinit var mockDecorViewMapper: DecorViewMapper

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @Mock
    lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockViewUtilsInternal.isNotVisible(any())).thenReturn(false)
        whenever(mockViewUtilsInternal.isSystemNoise(any())).thenReturn(false)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
        val fakeTypes: List<Class<out View>> = mockViews.map { it::class.java }
        val fakeTypeToMapperMap: Map<Class<out View>, WireframeMapper<View>> = fakeTypes
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
                any(),
                eq(mockInternalLogger)
            )
        )
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            fakeTypeMapperWrappers,
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
            .isEqualTo(TraversalStrategy.STOP_AND_RETURN_NODE)
    }

    @Test
    fun `M default to view mapper and stop W traverse { mapper for view type not provided }`(
        forge: Forge
    ) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe.ShapeWireframe> = forge.aList { getForgery() }
        val mockView = mock<View> {
            // Ensures the view is not treated as a decor view
            whenever(it.parent) doReturn mock<ViewGroup>()
        }
        whenever(mockDefaultViewMapper.map(eq(mockView), eq(fakeMappingContext), any(), eq(mockInternalLogger)))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
            .isEqualTo(TraversalStrategy.STOP_AND_RETURN_NODE)
    }

    @Test
    fun `M default to view mapper and continue W traverse { mapper for ViewGroup type not provided }`(
        forge: Forge
    ) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe.ShapeWireframe> =
            forge.aList { getForgery() }
        val mockView = mock<ViewGroup> {
            // Ensures the view is not treated as a decor view
            whenever(it.parent) doReturn mock<ViewGroup>()
        }
        whenever(mockDefaultViewMapper.map(eq(mockView), eq(fakeMappingContext), any(), eq(mockInternalLogger)))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
            .isEqualTo(TraversalStrategy.TRAVERSE_ALL_CHILDREN)
    }

    @Test
    fun `M use TRAVERSE_ALL_CHILDREN traversal strategy W traverse { TraverseAllChildrenMapper }`(
        forge: Forge
    ) {
        // Given
        val fakeViewMappedWireframes: List<MobileSegment.Wireframe> = forge.aList { getForgery() }
        val mockViews: List<ViewGroup> = listOf(
            forge.aMockView<FrameLayout>(),
            forge.aMockView<LinearLayout>(),
            forge.aMockView<DrawerLayout>()
        )
        val fakeTypes: List<Class<out ViewGroup>> = mockViews.map { it::class.java }
        val fakeTypeToMapperMap: Map<Class<out ViewGroup>, TraverseAllChildrenMapper<ViewGroup>> = fakeTypes
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
                any(),
                eq(mockInternalLogger)
            )
        )
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            fakeTypeMapperWrappers,
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
            .isEqualTo(TraversalStrategy.TRAVERSE_ALL_CHILDREN)
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
        whenever(mockDecorViewMapper.map(eq(mockView), eq(fakeMappingContext), any(), eq(mockInternalLogger)))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
            .isEqualTo(TraversalStrategy.TRAVERSE_ALL_CHILDREN)
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

        whenever(mockDecorViewMapper.map(eq(mockView), eq(fakeMappingContext), any(), eq(mockInternalLogger)))
            .thenReturn(fakeViewMappedWireframes)
        testedTreeViewTraversal = TreeViewTraversal(
            emptyList(),
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
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
            .isEqualTo(TraversalStrategy.TRAVERSE_ALL_CHILDREN)
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
            .isEqualTo(TraversalStrategy.STOP_AND_DROP_NODE)
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
            .isEqualTo(TraversalStrategy.STOP_AND_DROP_NODE)
    }

    // endregion

    // region MapWith Telemetry

    @Test
    fun `M return correct sample rate W traverse { ViewWireframeMapper }()`(forge: Forge) {
        // Given
        val expectedSampleRate = MethodCallSamplingRate.RARE.rate
        val mockViewGroup = forge.aMockView<ViewGroup>()

        val mockView = mock<View> {
            whenever(it.parent) doReturn mockViewGroup
        }

        // When
        testedTreeViewTraversal.traverse(
            mockView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        val expectedOperationName = "$METHOD_CALL_MAP_PREFIX ${ViewWireframeMapper::class.simpleName}"
        verify(mockInternalLogger).measureMethodCallPerf(
            callerClass = TreeViewTraversal::class.java,
            operationName = expectedOperationName,
            samplingRate = expectedSampleRate
        ) {}
    }

    @Test
    fun `M return correct sample rate W traverse { DecorViewMapper }()`() {
        // Given
        val expectedSampleRate = MethodCallSamplingRate.RARE.rate
        val mockView = mock<View> {
            whenever(it.parent) doReturn null
        }

        // When
        testedTreeViewTraversal.traverse(
            mockView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        val expectedOperationName = "$METHOD_CALL_MAP_PREFIX ${DecorViewMapper::class.simpleName}"
        verify(mockInternalLogger).measureMethodCallPerf(
            callerClass = TreeViewTraversal::class.java,
            operationName = expectedOperationName,
            samplingRate = expectedSampleRate
        ) {}
    }

    @Test
    fun `M return correct sample rate W traverse() { specific mapper }`(forge: Forge) {
        // Given
        val expectedSampleRate = MethodCallSamplingRate.RARE.rate
        val mockViewGroup = forge.aMockView<ViewGroup>()
        val mockDefaultView = mock<View> {
            whenever(it.parent) doReturn mockViewGroup
        }
        val mockMapper = mock<MapperTypeWrapper<*>>()
        val mockWireFrameMapper = mock<WireframeMapper<View>>()
        whenever(mockMapper.supportsView(mockDefaultView)).thenReturn(true)
        whenever(mockMapper.getUnsafeMapper()).thenReturn(mockWireFrameMapper)

        testedTreeViewTraversal = TreeViewTraversal(
            listOf(mockMapper),
            mockDefaultViewMapper,
            mockDecorViewMapper,
            mockViewUtilsInternal,
            mockInternalLogger
        )

        // When
        testedTreeViewTraversal.traverse(
            mockDefaultView,
            fakeMappingContext,
            mockRecordedDataQueueRefs
        )

        // Then
        val expectedOperationName = "$METHOD_CALL_MAP_PREFIX ${mockWireFrameMapper::class.simpleName}"
        verify(mockInternalLogger).measureMethodCallPerf(
            callerClass = TreeViewTraversal::class.java,
            operationName = expectedOperationName,
            samplingRate = expectedSampleRate
        ) {}
    }

    // endregion
}
