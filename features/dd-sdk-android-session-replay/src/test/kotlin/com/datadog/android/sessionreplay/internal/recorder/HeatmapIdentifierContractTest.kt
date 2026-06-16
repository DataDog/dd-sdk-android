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
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistryProvider
import com.datadog.android.internal.heatmaps.heatmapViewKey
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.LazyHeatmapIdentifierRegistry
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Contract test for the SR → RUM heatmap identifier pipeline.
 *
 * Verifies that the key used by Session Replay when publishing identifiers
 * (heatmapViewKey called inside HeatmapIdentifierResolver) is the same key
 * RUM uses when looking up identifiers at tap time (heatmapViewKey called by
 * the gesture layer), and that the real HeatmapIdentifierStore correctly
 * stores and serves the round-trip. All other tests in this area mock the
 * registry boundary, so this test is the only one that catches a key mismatch
 * between the two sides.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class HeatmapIdentifierContractTest {

    // The real store — shared between the SR write path and the RUM read path.
    private lateinit var realRegistry: HeatmapIdentifierRegistry

    private lateinit var testedSnapshotProducer: SnapshotProducer

    @Mock lateinit var mockSdkCore: FeatureSdkCore

    @Mock lateinit var mockTreeViewTraversal: TreeViewTraversal

    @Mock lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs

    @Mock lateinit var mockOptionSelectorDetector: DefaultOptionSelectorDetector

    @Mock lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @Mock lateinit var mockInternalLogger: InternalLogger

    @Mock lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Forgery lateinit var fakeSystemInformation: SystemInformation

    @Forgery lateinit var fakeTextAndInputPrivacy: TextAndInputPrivacy

    @Forgery lateinit var fakeImagePrivacy: ImagePrivacy

    @Forgery lateinit var fakeViewWireframes: List<MobileSegment.Wireframe>

    @StringForgery lateinit var fakeAppPackageName: String

    @BeforeEach
    fun `set up`() {
        realRegistry = HeatmapIdentifierRegistry.create()
        whenever(mockSdkCore.internalLogger).thenReturn(mockInternalLogger)
        val rumFeatureScope = stubRumFeatureWithRegistry(realRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(rumFeatureScope)
        whenever(mockTreeViewTraversal.traverse(any(), any(), any())).thenReturn(
            TreeViewTraversal.TraversedTreeView(fakeViewWireframes, TraversalStrategy.TRAVERSE_ALL_CHILDREN)
        )
        testedSnapshotProducer = SnapshotProducer(
            imageWireframeHelper = mockImageWireframeHelper,
            treeViewTraversal = mockTreeViewTraversal,
            optionSelectorDetector = mockOptionSelectorDetector,
            touchPrivacyManager = mockTouchPrivacyManager,
            internalLogger = mockInternalLogger,
            heatmapResolver = HeatmapIdentifierResolver(
                appPackageName = fakeAppPackageName,
                registry = LazyHeatmapIdentifierRegistry(mockSdkCore),
                internalLogger = mockInternalLogger
            )
        )
    }

    @Test
    fun `M return identifier W getHeatmapIdentifier() { SR records view, RUM looks up same view key }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val mockButton = forge.aMockTappableViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        )
        val mockRoot = forge.aMockNonTappableViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            children = listOf(mockButton)
        )

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(realRegistry.getHeatmapIdentifier(heatmapViewKey(mockButton), fakeViewUrl)).isNotNull()
    }

    @Test
    fun `M return null W getHeatmapIdentifier() { RUM looks up with different screen name than SR recorded }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String,
        @StringForgery fakeDifferentViewUrl: String
    ) {
        val differentUrl = if (fakeDifferentViewUrl != fakeViewUrl) fakeDifferentViewUrl else "$fakeViewUrl#other"
        val mockButton = forge.aMockTappableViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        )
        val mockRoot = forge.aMockNonTappableViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            children = listOf(mockButton)
        )

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(realRegistry.getHeatmapIdentifier(heatmapViewKey(mockButton), differentUrl)).isNull()
    }

    @Test
    fun `M return same identifier W getHeatmapIdentifier() { same view recorded on consecutive snapshots }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        val mockButton = forge.aMockTappableViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        )
        val mockRoot = forge.aMockNonTappableViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            children = listOf(mockButton)
        )

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )
        val firstIdentifier = realRegistry.getHeatmapIdentifier(heatmapViewKey(mockButton), fakeViewUrl)

        testedSnapshotProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )
        val secondIdentifier = realRegistry.getHeatmapIdentifier(heatmapViewKey(mockButton), fakeViewUrl)

        assertThat(firstIdentifier).isNotNull()
        assertThat(secondIdentifier).isEqualTo(firstIdentifier)
    }

    @Test
    fun `M return identifier W { SR initialised before RUM, first snapshot fires after RUM registers }`(
        forge: Forge,
        @StringForgery fakeViewUrl: String
    ) {
        // This test documents the SR-before-RUM initialisation order that occurs in the sample
        // app (and many real apps): SessionReplay.enable() is called before Rum.enable() in
        // Application.onCreate(). LazyHeatmapIdentifierRegistry is therefore created while
        // getFeature(RUM) returns null. By the time the first snapshot fires (after
        // Application.onCreate() completes and the first vsync draws the UI), RUM is registered.
        //
        // Without LazyHeatmapIdentifierRegistry the registry would be permanently null and
        // no permanentId would ever appear on wireframes.

        // LazyHeatmapIdentifierRegistry created — RUM not yet registered at this moment
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(null)
        val localRegistry = HeatmapIdentifierRegistry.create()
        val localProducer = SnapshotProducer(
            imageWireframeHelper = mockImageWireframeHelper,
            treeViewTraversal = mockTreeViewTraversal,
            optionSelectorDetector = mockOptionSelectorDetector,
            touchPrivacyManager = mockTouchPrivacyManager,
            internalLogger = mockInternalLogger,
            heatmapResolver = HeatmapIdentifierResolver(
                appPackageName = fakeAppPackageName,
                registry = LazyHeatmapIdentifierRegistry(mockSdkCore),
                internalLogger = mockInternalLogger
            )
        )

        // RUM registers (the next line in Application.onCreate())
        val rumFeatureScope = stubRumFeatureWithRegistry(localRegistry)
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)).thenReturn(rumFeatureScope)

        val mockButton = forge.aMockTappableViewWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}"
        )
        val mockRoot = forge.aMockNonTappableViewGroupWithResourceId(
            viewId = forge.anInt(min = 1, max = Int.MAX_VALUE),
            resourceName = "com.example.app:id/${forge.anAlphabeticalString()}",
            children = listOf(mockButton)
        )

        // First snapshot fires — both SR and RUM are now up, lazy registry resolves successfully
        localProducer.produce(
            rootView = mockRoot,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = fakeTextAndInputPrivacy,
            imagePrivacy = fakeImagePrivacy,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            activeRumViewUrl = fakeViewUrl
        )

        assertThat(localRegistry.getHeatmapIdentifier(heatmapViewKey(mockButton), fakeViewUrl)).isNotNull()
    }

    // region helpers

    private fun stubRumFeatureWithRegistry(registry: HeatmapIdentifierRegistry): FeatureScope {
        val mockRumFeature: Feature = mock()
        val provider = object : Feature by mockRumFeature, HeatmapIdentifierRegistryProvider {
            override val heatmapIdentifierRegistry = registry
        }
        return mock { whenever(it.unwrap<Feature>()).doReturn(provider) }
    }

    private fun Forge.aMockTappableViewWithResourceId(viewId: Int, resourceName: String): View {
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(resourceName)
        }
        return aMockView<View>().apply {
            whenever(id).thenReturn(viewId)
            whenever(resources).thenReturn(mockResources)
            whenever(isClickable).thenReturn(true)
            whenever(visibility).thenReturn(View.VISIBLE)
        }
    }

    private fun Forge.aMockNonTappableViewGroupWithResourceId(
        viewId: Int,
        resourceName: String,
        children: List<View>
    ): ViewGroup {
        val mockResources: Resources = mock {
            whenever(it.getResourceName(viewId)).thenReturn(resourceName)
        }
        return aMockView<ViewGroup>().apply {
            whenever(id).thenReturn(viewId)
            whenever(resources).thenReturn(mockResources)
            whenever(isClickable).thenReturn(false)
            whenever(visibility).thenReturn(View.VISIBLE)
            whenever(childCount).thenReturn(children.size)
            children.forEachIndexed { i, child -> whenever(getChildAt(i)).thenReturn(child) }
        }
    }

    // endregion
}
