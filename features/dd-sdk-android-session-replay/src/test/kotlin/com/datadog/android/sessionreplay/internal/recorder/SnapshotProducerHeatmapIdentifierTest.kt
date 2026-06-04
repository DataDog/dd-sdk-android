/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.heatmaps.heatmapViewKey
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotProducerHeatmapIdentifierTest {

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

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Mock
    lateinit var mockHeatmapIdentifierRegistry: HeatmapIdentifierRegistry

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Forgery
    lateinit var fakeViewWireframes: List<MobileSegment.Wireframe>

    @Forgery
    lateinit var fakeTextAndInputPrivacy: TextAndInputPrivacy

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @StringForgery
    lateinit var fakeAppPackageName: String

    @BeforeEach
    fun `set up`() {
        testedSnapshotProducer = SnapshotProducer(
            imageWireframeHelper = mockImageWireframeHelper,
            treeViewTraversal = mockTreeViewTraversal,
            optionSelectorDetector = mockOptionSelectorDetector,
            touchPrivacyManager = mockTouchPrivacyManager,
            internalLogger = mockInternalLogger,
            heatmapResolver = HeatmapIdentifierResolver(
                appPackageName = fakeAppPackageName,
                registry = mockHeatmapIdentifierRegistry,
                internalLogger = mockInternalLogger
            )
        )
    }

    // region produce — registry write semantics

    @Test
    fun `M skip heatmap work W produce() { viewUrl is null }`(forge: Forge) {
        val fakeRoot = forge.aMockView<View>()
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = null
        )

        assertThat(result?.heatmapIdentifier).isNull()
        verify(mockHeatmapIdentifierRegistry, never()).setHeatmapIdentifiers(any(), any())
    }

    @Test
    fun `M skip heatmap work W produce() { no registry available }`(forge: Forge) {
        val producerWithoutRegistry = SnapshotProducer(
            imageWireframeHelper = mockImageWireframeHelper,
            treeViewTraversal = mockTreeViewTraversal,
            optionSelectorDetector = mockOptionSelectorDetector,
            touchPrivacyManager = mockTouchPrivacyManager,
            internalLogger = mockInternalLogger,
            heatmapResolver = null
        )
        val fakeRoot = forge.aMockView<View>()
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val result = producerWithoutRegistry.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = forge.anAlphabeticalString()
        )

        assertThat(result?.heatmapIdentifier).isNull()
        verify(mockHeatmapIdentifierRegistry, never()).setHeatmapIdentifiers(any(), any())
    }

    @Test
    fun `M write to registry W produce() { viewUrl present, single leaf view }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString()
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        val identifiersCaptor = argumentCaptor<Map<Long, HeatmapIdentifier>>()
        verify(mockHeatmapIdentifierRegistry).setHeatmapIdentifiers(
            identifiersCaptor.capture(),
            eq(fakeViewUrl)
        )
        val identifiers = identifiersCaptor.firstValue
        assertThat(identifiers).hasSize(1)
        val rootKey = heatmapViewKey(fakeRoot)
        assertThat(identifiers).containsKey(rootKey)
        assertThat(result?.heatmapIdentifier).isEqualTo(identifiers[rootKey])
    }

    @Test
    fun `M not write to registry W produce() { entire tree dropped }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockView<View>()
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_DROP_NODE
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(result).isNull()
        verify(mockHeatmapIdentifierRegistry, never()).setHeatmapIdentifiers(any(), any())
    }

    @Test
    fun `M write distinct identifiers per view W produce() { multi-level tree }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val rootName = forge.anAlphabeticalString() + "_root"
        val child0Name = forge.anAlphabeticalString() + "_0"
        val child1Name = forge.anAlphabeticalString() + "_1"
        val mockChild0 = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = child0Name
        )
        val mockChild1 = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = child1Name
        )
        val mockRoot = forge.aMockViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = rootName,
            children = listOf(mockChild0, mockChild1)
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.TRAVERSE_ALL_CHILDREN
            )
        )

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        val identifiersCaptor = argumentCaptor<Map<Long, HeatmapIdentifier>>()
        verify(mockHeatmapIdentifierRegistry).setHeatmapIdentifiers(
            identifiersCaptor.capture(),
            eq(fakeViewUrl)
        )
        val identifiers = identifiersCaptor.firstValue
        assertThat(identifiers).hasSize(3)
        assertThat(identifiers).containsKey(heatmapViewKey(mockRoot))
        assertThat(identifiers).containsKey(heatmapViewKey(mockChild0))
        assertThat(identifiers).containsKey(heatmapViewKey(mockChild1))
        assertThat(identifiers.values.distinct()).hasSize(3)
    }

    // endregion

    // region cross-snapshot semantics — cache reuse and hierarchy-change detection

    @Test
    fun `M skip registry write W produce() { same screen, same entries on consecutive snapshots }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString()
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val firstResult = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )
        val secondResult = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        verify(mockHeatmapIdentifierRegistry).setHeatmapIdentifiers(any(), eq(fakeViewUrl))
        assertThat(firstResult?.heatmapIdentifier).isNotNull()
        assertThat(secondResult?.heatmapIdentifier).isNotNull()
    }

    @Test
    fun `M rewrite registry W produce() { same view but different screen on second snapshot }`(
        forge: Forge,
        @StringForgery fakeFirstViewUrl: String,
        @StringForgery fakeSecondViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString()
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val firstResult = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeFirstViewUrl
        )
        val secondResult = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeSecondViewUrl
        )

        verify(mockHeatmapIdentifierRegistry).setHeatmapIdentifiers(any(), eq(fakeFirstViewUrl))
        verify(mockHeatmapIdentifierRegistry).setHeatmapIdentifiers(any(), eq(fakeSecondViewUrl))
        assertThat(firstResult?.heatmapIdentifier).isNotEqualTo(secondResult?.heatmapIdentifier)
    }

    @Test
    fun `M reuse cached identifier W produce() { same view on consecutive snapshots, same screen }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString()
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val firstResult = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )
        val secondResult = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(firstResult?.heatmapIdentifier).isNotNull
        assertThat(secondResult?.heatmapIdentifier).isEqualTo(firstResult?.heatmapIdentifier)
    }

    @Test
    fun `M drop identifier from registry W produce() { view becomes non-clickable on second snapshot }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val clickableViewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val transitionViewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val mockClickable = forge.aMockViewWithResourceId(
            viewId = clickableViewId,
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            isClickable = true
        )
        val mockTransition = forge.aMockViewWithResourceId(
            viewId = transitionViewId,
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            isClickable = true
        )
        val mockRoot = forge.aMockViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            children = listOf(mockClickable, mockTransition),
            isClickable = false
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.TRAVERSE_ALL_CHILDREN
            )
        )

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        whenever(mockTransition.isClickable).thenReturn(false)

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        val identifiersCaptor = argumentCaptor<Map<Long, HeatmapIdentifier>>()
        verify(mockHeatmapIdentifierRegistry, times(2))
            .setHeatmapIdentifiers(identifiersCaptor.capture(), eq(fakeViewUrl))
        val firstWrite = identifiersCaptor.firstValue
        val secondWrite = identifiersCaptor.secondValue
        val transitionKey = heatmapViewKey(mockTransition)
        val clickableKey = heatmapViewKey(mockClickable)
        assertThat(firstWrite).containsKey(transitionKey)
        assertThat(firstWrite).containsKey(clickableKey)
        assertThat(secondWrite).doesNotContainKey(transitionKey)
        assertThat(secondWrite).containsKey(clickableKey)
    }

    // endregion

    // region tap-target gating

    @Test
    fun `M skip identifier W produce() { non-clickable leaf view }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString(),
            isClickable = false
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(result?.heatmapIdentifier).isNull()
        verify(mockHeatmapIdentifierRegistry, never()).setHeatmapIdentifiers(any(), any())
    }

    @Test
    fun `M skip identifier W produce() { clickable but INVISIBLE view }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString(),
            isClickable = true
        )
        whenever(fakeRoot.visibility).thenReturn(View.INVISIBLE)
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(result?.heatmapIdentifier).isNull()
        verify(mockHeatmapIdentifierRegistry, never()).setHeatmapIdentifiers(any(), any())
    }

    @Test
    fun `M skip identifier W produce() { clickable but GONE view }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val fakeRoot = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = forge.anAlphabeticalString(),
            isClickable = true
        )
        whenever(fakeRoot.visibility).thenReturn(View.GONE)
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.STOP_AND_RETURN_NODE
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = fakeRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(result?.heatmapIdentifier).isNull()
        verify(mockHeatmapIdentifierRegistry, never()).setHeatmapIdentifiers(any(), any())
    }

    @Test
    fun `M register only the clickable child W produce() { non-clickable parent + clickable child }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val parentResourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        val childResourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        val mockChild = forge.aMockViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = childResourceName,
            isClickable = true
        )
        val mockParent = forge.aMockViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = parentResourceName,
            children = listOf(mockChild),
            isClickable = false
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(
                fakeViewWireframes,
                TraversalStrategy.TRAVERSE_ALL_CHILDREN
            )
        )

        val result = testedSnapshotProducer.produce(
            rootView = mockParent,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(result?.heatmapIdentifier).isNull()
        val identifiersCaptor = argumentCaptor<Map<Long, HeatmapIdentifier>>()
        verify(mockHeatmapIdentifierRegistry).setHeatmapIdentifiers(
            identifiersCaptor.capture(),
            eq(fakeViewUrl)
        )
        val identifiers = identifiersCaptor.firstValue
        assertThat(identifiers).hasSize(1)
        val parentKey = heatmapViewKey(mockParent)
        val childKey = heatmapViewKey(mockChild)
        assertThat(identifiers).containsKey(childKey)
        assertThat(identifiers).doesNotContainKey(parentKey)
    }

    // endregion

    // region cross-snapshot semantics — stale cache resurrection

    @Test
    fun `M produce fresh identifiers W produce() { tap targets disappear then reappear }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val viewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val resourceName = forge.anAlphabeticalString()

        val mockView = forge.aMockViewWithResourceId(viewId = viewId, resourceName = resourceName)
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(fakeViewWireframes, TraversalStrategy.STOP_AND_RETURN_NODE)
        )
        val firstResult = testedSnapshotProducer.produce(
            rootView = mockView,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )
        assertThat(firstResult?.heatmapIdentifier).isNotNull

        whenever(mockView.isClickable).thenReturn(false)
        testedSnapshotProducer.produce(
            rootView = mockView,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        whenever(mockView.isClickable).thenReturn(true)
        val thirdResult = testedSnapshotProducer.produce(
            rootView = mockView,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        verify(mockHeatmapIdentifierRegistry, times(2)).setHeatmapIdentifiers(any(), eq(fakeViewUrl))
        assertThat(thirdResult?.heatmapIdentifier).isNotNull
        assertThat(thirdResult?.heatmapIdentifier).isEqualTo(firstResult?.heatmapIdentifier)
    }

    @Test
    fun `M recompute and update registry W produce() { tap target moves to different sibling position }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val sharedResourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        val sharedViewId = forge.anInt(min = 1, max = Int.MAX_VALUE)
        val mockChild0 = forge.aMockViewWithResourceId(sharedViewId, sharedResourceName)
        val mockChild1 = forge.aMockViewWithResourceId(sharedViewId, sharedResourceName)
        val mockRoot = forge.aMockViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            children = listOf(mockChild0, mockChild1)
        )
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(fakeViewWireframes, TraversalStrategy.TRAVERSE_ALL_CHILDREN)
        )

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild1)
        whenever(mockRoot.getChildAt(1)).thenReturn(mockChild0)

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        val captor = argumentCaptor<Map<Long, HeatmapIdentifier>>()
        verify(mockHeatmapIdentifierRegistry, times(2)).setHeatmapIdentifiers(
            captor.capture(),
            eq(fakeViewUrl)
        )
        val firstIds = captor.firstValue
        val secondIds = captor.allValues[1]

        assertThat(secondIds[heatmapViewKey(mockChild0)]).isNotEqualTo(firstIds[heatmapViewKey(mockChild0)])
        assertThat(secondIds[heatmapViewKey(mockChild1)]).isNotEqualTo(firstIds[heatmapViewKey(mockChild1)])
        assertThat(secondIds[heatmapViewKey(mockChild0)]).isEqualTo(firstIds[heatmapViewKey(mockChild1)])
        assertThat(secondIds[heatmapViewKey(mockChild1)]).isEqualTo(firstIds[heatmapViewKey(mockChild0)])
    }

    // endregion

    // region helpers

    private fun Forge.aMockViewWithResourceId(
        viewId: Int,
        resourceName: String,
        isClickable: Boolean = true
    ): View {
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(resourceName)
        }
        return aMockView<View>().apply {
            whenever(this.id).thenReturn(viewId)
            whenever(this.resources).thenReturn(mockResources)
            whenever(this.isClickable).thenReturn(isClickable)
            whenever(this.visibility).thenReturn(View.VISIBLE)
        }
    }

    private fun Forge.aMockViewGroupWithResourceId(
        viewId: Int,
        resourceName: String,
        children: List<View>,
        isClickable: Boolean = true
    ): ViewGroup {
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(resourceName)
        }
        return aMockView<ViewGroup>().apply {
            whenever(this.id).thenReturn(viewId)
            whenever(this.resources).thenReturn(mockResources)
            whenever(this.isClickable).thenReturn(isClickable)
            whenever(this.visibility).thenReturn(View.VISIBLE)
            whenever(this.childCount).thenReturn(children.size)
            children.forEachIndexed { i, child ->
                whenever(this.getChildAt(i)).thenReturn(child)
            }
        }
    }

    // endregion
}
