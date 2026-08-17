/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import androidx.annotation.MainThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * The real [CapturedSnapshotProducer] for the plain Android View hierarchy - the workstream-3
 * implementation of the extension point [SnapshotCaptureOrchestrator] drives every generation.
 * Builds a fresh [CapturedIdentityFactory] per call (this workstream only produces full snapshots;
 * an identity factory persisting across generations for incremental diffing is a later workstream's
 * concern), walks every currently active window via [AndroidWindowTraversal], and assembles them
 * under one synthetic screen root in [ActiveWindowSource.currentWindows] order (already z-ordered).
 */
internal class AndroidCapturedSnapshotProducer(
    private val windowSource: ActiveWindowSource,
    private val scopeProvider: RumViewScopeProvider,
    private val timeProvider: TimeProvider,
    private val traversal: AndroidWindowTraversal,
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
) : CapturedSnapshotProducer {

    @MainThread
    override fun capture(context: CaptureGenerationContext, changeset: CaptureChangeset): CapturedFullSnapshot? {
        val rumViewScope = scopeProvider.currentScope() ?: return null
        val identityFactory = DefaultCapturedIdentityFactory(rumViewScope.scope)
        val walk = walkWindows(windowSource.currentWindows(), identityFactory, context)

        return walk?.let {
            val root = CapturedLayer(
                identity = identityFactory.screenRoot(),
                kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
                bounds = it.windowLayers.firstOrNull()?.bounds ?: CapturedBounds(0, 0, 0, 0),
                children = it.windowLayers.map { layer -> CapturedChild.Layer(layer.identity) }
            )
            CapturedFullSnapshot(
                timestamp = timeProvider.getDeviceTimestampMillis() + rumViewScope.viewTimeOffsetMs,
                scope = rumViewScope.scope,
                root = root,
                layers = it.layers,
                wireframes = it.wireframes
            )
        }
    }

    private fun walkWindows(
        windows: List<View>,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext
    ): WindowsWalkAccumulation? {
        if (windows.isEmpty()) return null
        val layers = mutableListOf<CapturedLayer>()
        val wireframes = mutableListOf<CapturedWireframe>()
        val windowLayers = mutableListOf<CapturedLayer>()
        var aborted = false

        for (window in windows) {
            val windowIdentity = identityFactory.window(viewIdentifierResolver.resolveViewId(window).toString())
            when (val result = traversal.traverseWindow(window, windowIdentity, identityFactory, context)) {
                is WindowWalkResult.Present -> {
                    windowLayers += result.rootLayer
                    layers += result.layers
                    wireframes += result.wireframes
                }
                WindowWalkResult.Filtered -> Unit
                WindowWalkResult.Aborted -> {
                    aborted = true
                }
            }
            if (aborted) break
        }

        return if (aborted) null else WindowsWalkAccumulation(windowLayers, layers, wireframes)
    }

    private class WindowsWalkAccumulation(
        val windowLayers: List<CapturedLayer>,
        val layers: List<CapturedLayer>,
        val wireframes: List<CapturedWireframe>
    )
}
