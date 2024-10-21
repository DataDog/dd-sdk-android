/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.PrivacyHelper.Companion.INVALID_PRIVACY_LEVEL_ERROR
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.setSessionReplayImagePrivacy
import com.datadog.android.sessionreplay.setSessionReplayTextAndInputPrivacy
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.LinkedList

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotProducerTest {

    private lateinit var testedSnapshotProducer: SnapshotProducer

    @Mock
    lateinit var mockTreeViewTraversal: TreeViewTraversal

    @Mock
    lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs

    @Mock
    lateinit var mockOptionSelectorDetector: DefaultOptionSelectorDetector

    @Mock
    lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Forgery
    lateinit var fakeViewWireframes: List<MobileSegment.Wireframe>

    @Forgery
    lateinit var fakeTextAndInputPrivacy: TextAndInputPrivacy

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @BeforeEach
    fun `set up`() {
        testedSnapshotProducer = SnapshotProducer(
            mockImageWireframeHelper,
            mockTreeViewTraversal,
            mockOptionSelectorDetector,
            mockInternalLogger
        )
    }

    @Test
    fun `M produce a null Node W produce(){ STOP_AND_DROP strategy }`() {
        // Given
        val mockRoot: View = mock()
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.STOP_AND_DROP_NODE
        )
        whenever(mockTreeViewTraversal.traverse(eq(mockRoot), any(), any()))
            .thenReturn(fakeTraversedTreeView)

        // When
        val snapshot = testedSnapshotProducer.produce(
            mockRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(snapshot).isNull()
    }

    @Test
    fun `M produce a single Node W produce() { leaf view, STOP_AND_RETURN strategy }`(
        forge: Forge
    ) {
        // Given
        val fakeRoot = forge.aMockView<View>()
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.STOP_AND_RETURN_NODE
        )
        whenever(mockTreeViewTraversal.traverse(eq(fakeRoot), any(), any()))
            .thenReturn(fakeTraversedTreeView)
        val expectedSnapshot = fakeRoot.toNode(viewMappedWireframes = fakeViewWireframes)

        // When
        val snapshot = testedSnapshotProducer.produce(
            fakeRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(snapshot).isEqualTo(expectedSnapshot)
    }

    @Test
    fun `M produce a single Node W produce() { tree view, STOP_AND_RETURN strategy }`(
        forge: Forge
    ) {
        // Given
        val fakeRoot = forge.aMockViewWithChildren(2, 0, 2)
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.STOP_AND_RETURN_NODE
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any()))
            .thenReturn(fakeTraversedTreeView)
        val expectedSnapshot = Node(wireframes = fakeViewWireframes)

        // When
        val snapshot = testedSnapshotProducer.produce(
            fakeRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(snapshot).isEqualTo(expectedSnapshot)
    }

    @Test
    fun `M produce a tree of Nodes W produce(){ tree view, TRAVERSE_ALL_CHILDREN strategy  }`(
        forge: Forge
    ) {
        // Given
        val fakeRoot = forge.aMockViewWithChildren(2, 0, 2)
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any()))
            .thenReturn(fakeTraversedTreeView)
        val expectedSnapshot = fakeRoot.toNode(viewMappedWireframes = fakeViewWireframes)

        // When
        val snapshot = testedSnapshotProducer.produce(
            fakeRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(snapshot).isEqualTo(expectedSnapshot)
    }

    @Test
    fun `M create the MappingContext W produce()`(
        forge: Forge
    ) {
        // Given
        val mockChildren: List<View> = forge.aList { mock() }
        val mockRoot: ViewGroup = mock { root ->
            whenever(root.childCount).thenReturn(mockChildren.size)
            whenever(root.getChildAt(any())).thenAnswer { mockChildren[it.getArgument(0)] }
        }
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            fakeTraversedTreeView
        )

        // When
        testedSnapshotProducer.produce(
            mockRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        val argumentCaptor = argumentCaptor<MappingContext>()
        verify(mockTreeViewTraversal, times(1 + mockChildren.size))
            .traverse(any(), argumentCaptor.capture(), any())
        argumentCaptor.allValues.forEach {
            assertThat(it.systemInformation).isEqualTo(fakeSystemInformation)
            assertThat(it.imageWireframeHelper).isEqualTo(mockImageWireframeHelper)
            assertThat(it.textAndInputPrivacy).isEqualTo(fakeTextAndInputPrivacy)
        }
    }

    @Test
    fun `M update the MappingContext W produce(){ OptionSelect container type  }`(
        forge: Forge
    ) {
        // Given
        val mockChildren: List<View> = forge.aList { mock() }
        val mockRoot: ViewGroup = mock { root ->
            whenever(root.childCount).thenReturn(mockChildren.size)
            whenever(root.getChildAt(any())).thenAnswer { mockChildren[it.getArgument(0)] }
        }
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(fakeTraversedTreeView)
        whenever(mockOptionSelectorDetector.isOptionSelector(mockRoot)).thenReturn(true)

        // When
        testedSnapshotProducer.produce(
            mockRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        val argumentCaptor = argumentCaptor<MappingContext>()
        mockChildren.forEach {
            verify(mockTreeViewTraversal).traverse(eq(it), argumentCaptor.capture(), any())
        }
        argumentCaptor.allValues.forEach {
            assertThat(it.hasOptionSelectorParent).isTrue
            assertThat(it.systemInformation).isEqualTo(fakeSystemInformation)
        }
    }

    @Test
    fun `M not update the MappingContext W produce(){ OptionSelect not container type  }`(
        forge: Forge
    ) {
        val mockChildren: List<View> = forge.aList { mock() }
        val mockRoot: ViewGroup = mock { root ->
            whenever(root.childCount).thenReturn(mockChildren.size)
            whenever(root.getChildAt(any())).thenAnswer { mockChildren[it.getArgument(0)] }
        }
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(fakeTraversedTreeView)
        whenever(mockOptionSelectorDetector.isOptionSelector(mockRoot)).thenReturn(false)

        // When
        testedSnapshotProducer.produce(
            mockRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        val argumentCaptor = argumentCaptor<MappingContext>()
        mockChildren.forEach {
            verify(mockTreeViewTraversal).traverse(eq(it), argumentCaptor.capture(), any())
        }
        argumentCaptor.allValues.forEach {
            assertThat(it.hasOptionSelectorParent).isFalse
            assertThat(it.systemInformation).isEqualTo(fakeSystemInformation)
        }
    }

    @Test
    fun `M produce a tree of Nodes W produce(){ tree view, TRAVERSE_ALL and STOP strategies  }`(
        forge: Forge
    ) {
        // Given
        val fakeRoot = forge.aMockViewWithChildren(2, 0, 2)
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any()))
            .thenReturn(fakeTraversedTreeView)
            .thenReturn(
                fakeTraversedTreeView.copy(
                    nextActionStrategy =
                    TraversalStrategy.STOP_AND_RETURN_NODE
                )
            )
        var expectedSnapshot = fakeRoot.toNode(viewMappedWireframes = fakeViewWireframes)
        expectedSnapshot = expectedSnapshot.copy(
            children = expectedSnapshot.children.map {
                it.copy(children = emptyList())
            }
        )

        // When
        val snapshot = testedSnapshotProducer.produce(
            fakeRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(snapshot).isEqualTo(expectedSnapshot)
    }

    @Test
    fun `M produce a tree of Nodes W produce(){ tree view, TRAVERSE_ALL and DROP strategies  }`(
        forge: Forge
    ) {
        // Given
        val fakeRoot = forge.aMockViewWithChildren(2, 0, 2)
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any()))
            .thenReturn(fakeTraversedTreeView)
            .thenReturn(
                fakeTraversedTreeView.copy(
                    nextActionStrategy =
                    TraversalStrategy.STOP_AND_DROP_NODE
                )
            )
        val expectedSnapshot = fakeRoot.toNode(viewMappedWireframes = fakeViewWireframes)
            .copy(children = emptyList())
        // When
        val snapshot = testedSnapshotProducer.produce(
            fakeRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        assertThat(snapshot).isEqualTo(expectedSnapshot)
    }

    @Test
    fun `M apply override privacy to parent and children W produce()`(
        forge: Forge
    ) {
        // Given
        val mockChildren: List<View> = forge.aList { mock() }
        val mockRoot: ViewGroup = mock { root ->
            whenever(root.childCount).thenReturn(mockChildren.size)
            whenever(root.getChildAt(any())).thenAnswer { mockChildren[it.getArgument(0)] }
        }
        val fakeImagePrivacy = forge.aValueFrom(ImagePrivacy::class.java)
        val fakeTextAndInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java)
        mockRoot.setSessionReplayImagePrivacy(fakeImagePrivacy)
        mockRoot.setSessionReplayTextAndInputPrivacy(fakeTextAndInputPrivacy)
        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any()))
            .thenReturn(fakeTraversedTreeView)
            .thenReturn(
                fakeTraversedTreeView.copy(
                    nextActionStrategy =
                    TraversalStrategy.STOP_AND_DROP_NODE
                )
            )

        // When
        testedSnapshotProducer.produce(
            mockRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        val argumentCaptor = argumentCaptor<MappingContext>()
        verify(mockTreeViewTraversal, times(1 + mockChildren.size))
            .traverse(any(), argumentCaptor.capture(), any())
        argumentCaptor.allValues.forEach {
            assertThat(it.imagePrivacy).isEqualTo(fakeImagePrivacy)
            assertThat(it.textAndInputPrivacy).isEqualTo(fakeTextAndInputPrivacy)
        }
    }

    @Test
    fun `M log invalid privacy level W produce() { invalid override tag value }`(
        forge: Forge
    ) {
        // Given
        val mockChildren: List<View> = forge.aList { mock() }
        val mockRoot: ViewGroup = mock { root ->
            whenever(root.childCount).thenReturn(mockChildren.size)
            whenever(root.getChildAt(any())).thenAnswer { mockChildren[it.getArgument(0)] }
            whenever(root.getTag(R.id.datadog_image_privacy)).thenReturn("arglblargl")
            whenever(root.getTag(R.id.datadog_text_and_input_privacy)).thenReturn("arglblargl")
        }
        val fakeImagePrivacy = forge.aValueFrom(ImagePrivacy::class.java)
        val fakeTextAndInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java)

        val fakeTraversedTreeView = TreeViewTraversal.TraversedTreeView(
            fakeViewWireframes,
            TraversalStrategy.TRAVERSE_ALL_CHILDREN
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any()))
            .thenReturn(fakeTraversedTreeView)
            .thenReturn(
                fakeTraversedTreeView.copy(
                    nextActionStrategy =
                    TraversalStrategy.STOP_AND_DROP_NODE
                )
            )

        // When
        testedSnapshotProducer.produce(
            mockRoot,
            fakeSystemInformation,
            fakeTextAndInputPrivacy,
            fakeImagePrivacy,
            mockRecordedDataQueueRefs
        )

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger, times(2)).log(
                eq(InternalLogger.Level.ERROR),
                eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
                capture(),
                any(),
                eq(false),
                eq(null)
            )
            assertThat(lastValue.invoke()).isEqualTo(INVALID_PRIVACY_LEVEL_ERROR)
        }
    }

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
