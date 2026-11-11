/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayHeatmapScreenRootProvider
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class HeatmapProviderCaptureTest {
    @StringForgery
    lateinit var fakePackage: String

    @StringForgery
    lateinit var fakeUrl: String

    @StringForgery
    lateinit var fakeScreenKey: String

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Forgery
    lateinit var fakeTextPrivacy: TextAndInputPrivacy

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @Forgery
    lateinit var fakeWireframes: List<MobileSegment.Wireframe>

    private lateinit var testedProducer: SnapshotProducer
    private lateinit var mockProvider: SessionReplayHeatmapScreenRootProvider
    private lateinit var mockRegistry: HeatmapIdentifierRegistry
    private lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs
    private lateinit var mockRoot: ViewGroup
    private lateinit var mockBranch: ViewGroup
    private lateinit var mockBranchTarget: View
    private lateinit var mockSiblingTarget: View

    @BeforeEach
    fun setUp() {
        mockProvider = mock()
        mockRegistry = mock()
        mockRecordedDataQueueRefs = mock()
        mockRoot = mock()
        mockBranch = mock()
        mockBranchTarget = clickableView()
        mockSiblingTarget = clickableView()
        whenever(mockRoot.id).thenReturn(View.NO_ID)
        whenever(mockRoot.childCount).thenReturn(2)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockBranch)
        whenever(mockRoot.getChildAt(1)).thenReturn(mockSiblingTarget)
        whenever(mockBranch.id).thenReturn(View.NO_ID)
        whenever(mockBranch.childCount).thenReturn(1)
        whenever(mockBranch.getChildAt(0)).thenReturn(mockBranchTarget)
        whenever(mockProvider.getHeatmapScreenRootKey(mockSiblingTarget)).thenReturn(fakeScreenKey)
        val mockTraversal = mock<TreeViewTraversal>()
        whenever(mockTraversal.traverse(any(), any(), eq(mockRecordedDataQueueRefs))).thenReturn(
            TreeViewTraversal.TraversedTreeView(fakeWireframes, TraversalStrategy.TRAVERSE_ALL_CHILDREN)
        )
        testedProducer = SnapshotProducer(
            imageWireframeHelper = mock<ImageWireframeHelper>(),
            treeViewTraversal = mockTraversal,
            optionSelectorDetector = mock<DefaultOptionSelectorDetector>(),
            touchPrivacyManager = mock<TouchPrivacyManager>(),
            internalLogger = mock<InternalLogger>(),
            heatmapResolver = HeatmapIdentifierResolver(
                fakePackage,
                mockRegistry,
                mock<InternalLogger>(),
                mockProvider
            )
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve capture and omit affected identifiers W produce() {provider failure}`(fakeFailureAtRoot: Boolean) {
        // Given
        val mockFailingView = if (fakeFailureAtRoot) mockRoot else mockBranch
        whenever(mockProvider.getHeatmapScreenRootKey(mockFailingView)).thenThrow(IllegalStateException())

        // When
        val result = assertDoesNotThrow<Node?> {
            testedProducer.produce(
                mockRoot,
                fakeSystemInformation,
                fakeTextPrivacy,
                fakeImagePrivacy,
                mockRecordedDataQueueRefs,
                fakeUrl
            )
        }

        // Then
        val root = checkNotNull(result)
        assertThat(root.children).hasSize(2)
        assertThat(root.wireframes).isSameAs(fakeWireframes)
        val branch = root.children[0]
        assertThat(branch.wireframes).isSameAs(fakeWireframes)
        assertThat(branch.children).hasSize(1)
        assertThat(branch.heatmapIdentifier).isNull()
        assertThat(branch.children[0].heatmapIdentifier).isNull()
        assertThat(branch.children[0].wireframes).isSameAs(fakeWireframes)
        verify(mockProvider, never()).getHeatmapScreenRootKey(mockBranchTarget)
        val entries = argumentCaptor<Map<Long, HeatmapIdentifier>>()
        verify(mockRegistry).setHeatmapIdentifiers(entries.capture(), eq(fakeUrl))
        if (fakeFailureAtRoot) {
            assertThat(root.children[1].heatmapIdentifier).isNull()
            assertThat(entries.firstValue).isEmpty()
        } else {
            val siblingIdentifier = checkNotNull(root.children[1].heatmapIdentifier)
            assertThat(entries.firstValue.values).containsExactly(siblingIdentifier)
        }
    }

    private fun clickableView(): View = mock {
        on { id }.thenReturn(View.NO_ID)
        on { isClickable }.thenReturn(true)
        on { visibility }.thenReturn(View.VISIBLE)
    }
}
