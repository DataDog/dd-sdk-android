/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import android.os.SystemClock
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.platform.ComposeView
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.ComposeContext
import com.datadog.android.sessionreplay.compose.internal.data.ComposeFields
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.findComposer
import com.datadog.android.sessionreplay.compose.internal.getSubComposers
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * A wireframe mapper able to convert a Jetpack Compose view into a Datadog Session Replay Wireframe.
 * @property privacy the privacy level to use when tracking composable views
 */
internal class ComposeWireframeMapper(
    val privacy: SessionReplayPrivacy
) : BaseWireframeMapper<ComposeView, MobileSegment.Wireframe>() {

    private val mappers = mapOf<String, CompositionGroupMapper>(
        // TODO: RUM-0000 Implement mappers for different Composable groups
        // "Text"
        // "Button"
        // "TabRow" : holds the tab row bg color
        //  "Tab": holds selected tab info
    )

    override fun map(
        view: ComposeView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val density = mappingContext.systemInformation.screenDensity.let { if (it == 0.0f) 1.0f else it }

        val composer = findComposer(view)
        return if (composer == null) {
            createPlaceholderWireframe(view, density)
        } else {
            val wireframes = createComposerWireframes(composer, density)
            wireframes
        }
    }

    // region Wireframes

    private fun createPlaceholderWireframe(
        view: ComposeView,
        density: Float
    ): List<MobileSegment.Wireframe> {
        val viewGlobalBounds = resolveViewGlobalBounds(view, density)
        return listOf(
            MobileSegment.Wireframe.PlaceholderWireframe(
                id = resolveViewId(view),
                x = viewGlobalBounds.x,
                y = viewGlobalBounds.y,
                width = viewGlobalBounds.width,
                height = viewGlobalBounds.height,
                label = "{Compose View}"
            )
        )
    }

    private fun createComposerWireframes(composer: Composer, density: Float): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        val startNs = SystemClock.elapsedRealtimeNanos()
        createComposerWireframes(composer, wireframes, UiContext(null, density))
        val stopNs = SystemClock.elapsedRealtimeNanos()

        val totalQuery = ComposeContext.contextCache.hitCount() + ComposeContext.contextCache.missCount()
        val hitRatio = ComposeContext.contextCache.hitCount() * HUNDRED / totalQuery
        val missRatio = ComposeContext.contextCache.missCount() * HUNDRED / totalQuery

        // During development, let's keep this telemetry to ensure we keep track of our performance
        (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.TELEMETRY,
            { "Parsed Compose Hierarchy ${System.currentTimeMillis()}" },
            additionalProperties = mapOf(
                "implementation" to "Datadog",
                "fullParsing" to (stopNs - startNs),
                "cache.hitRatio" to hitRatio,
                "cache.missRatio" to missRatio,
                "reflection.fieldsCache" to ComposeFields.fieldsCache.size
            )
        )
        return wireframes
    }

    private fun createComposerWireframes(
        composer: Composer,
        wireframes: MutableList<MobileSegment.Wireframe>,
        parentUiContext: UiContext
    ) {
        val compositionGroups = composer.compositionData.compositionGroups
        compositionGroups.forEach {
            createCompositionGroupWireframes(it, wireframes, parentUiContext)
        }
    }

    private fun createCompositionGroupWireframes(
        compositionGroup: CompositionGroup,
        wireframes: MutableList<MobileSegment.Wireframe>,
        parentUiContext: UiContext
    ) {
        var childrenUiContext = parentUiContext
        val composeContext = ComposeContext.from(compositionGroup)
        if (composeContext != null && !composeContext.name.isNullOrBlank()) {
            val composeWireframe = mapCompositionGroup(compositionGroup, composeContext, childrenUiContext)
            if (composeWireframe != null) {
                wireframes.add(composeWireframe.wireframe)
                childrenUiContext = composeWireframe.uiContext ?: parentUiContext
            }
        }

        compositionGroup.compositionGroups.forEach {
            createCompositionGroupWireframes(it, wireframes, childrenUiContext)
        }

        compositionGroup.data.asSequence()
            .flatMap { getSubComposers(it) }
            .forEach { createComposerWireframes(it, wireframes, childrenUiContext) }
    }

    private fun mapCompositionGroup(
        compositionGroup: CompositionGroup,
        composeContext: ComposeContext,
        parentUiContext: UiContext
    ): ComposeWireframe? {
        val mapper = mappers[composeContext.name] ?: return null
        return mapper.map(compositionGroup, composeContext, parentUiContext)
    }

    // endregion

    companion object {
        private const val HUNDRED: Double = 100.0
    }
}
