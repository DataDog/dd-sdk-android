/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import androidx.annotation.MainThread
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * The real [CapturedSnapshotProducer] for the plain Android View hierarchy - the workstream-3
 * implementation of the extension point [SnapshotCaptureOrchestrator] drives every generation.
 * Retains one [CapturedIdentityFactory] per active [com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope],
 * reusing it across generations so the same View keeps the same identity - required for
 * [CapturedSnapshotDiffer] to mean anything - and minting a fresh one, implicitly starting a new
 * identity scope, only when the RUM view changes. Walks every currently active window via
 * [AndroidWindowTraversal], and assembles them under one synthetic screen root in
 * [ActiveWindowSource.currentWindows] order (already z-ordered).
 */
internal class AndroidCapturedSnapshotProducer(
    private val windowSource: ActiveWindowSource,
    private val scopeProvider: RumViewScopeProvider,
    private val timeProvider: TimeProvider,
    private val traversal: AndroidWindowTraversal,
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
) : CapturedSnapshotProducer {

    private var retainedIdentityFactory: DefaultCapturedIdentityFactory? = null

    @MainThread
    @Suppress("ReturnCount")
    override fun capture(context: CaptureGenerationContext, changeset: CaptureChangeset): CaptureOutput? {
        val rumViewScope = scopeProvider.currentScope() ?: return null
        val identityFactory = identityFactoryFor(rumViewScope.scope)
        val walk = walkWindows(windowSource.currentWindows(), identityFactory, context, rumViewScope.viewUrl)

        return walk?.let {
            val root = CapturedLayer(
                identity = identityFactory.screenRoot(),
                kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
                bounds = it.windowLayers.firstOrNull()?.bounds ?: CapturedBounds(0, 0, 0, 0),
                children = it.windowLayers.map { layer -> CapturedChild.Layer(layer.identity) }
            )
            CaptureOutput(
                snapshot = CapturedFullSnapshot(
                    timestamp = timeProvider.getDeviceTimestampMillis() + rumViewScope.viewTimeOffsetMs,
                    scope = rumViewScope.scope,
                    root = root,
                    layers = it.layers,
                    wireframes = it.wireframes
                ),
                pendingPixelCaptures = it.pendingPixelCaptures,
                identityFactory = identityFactory
            )
        }
    }

    /** Reuses the retained factory while the RUM view scope is unchanged; mints a fresh one otherwise. */
    private fun identityFactoryFor(scope: RumViewIdentityScope): DefaultCapturedIdentityFactory {
        retainedIdentityFactory?.takeIf { it.scope == scope }?.let { return it }
        return DefaultCapturedIdentityFactory(scope).also { retainedIdentityFactory = it }
    }

    @MainThread
    private fun walkWindows(
        windows: List<View>,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        viewUrl: String?
    ): WindowsWalkAccumulation? {
        if (windows.isEmpty()) return null
        val layers = mutableListOf<CapturedLayer>()
        val wireframes = mutableListOf<CapturedWireframe>()
        val windowLayers = mutableListOf<CapturedLayer>()
        val pendingPixelCaptures = mutableListOf<PendingPixelCapture>()
        var aborted = false

        for (window in windows) {
            val windowIdentity = identityFactory.window(viewIdentifierResolver.resolveViewId(window).toString())
            when (
                val result = traversal.traverseWindow(window, windowIdentity, identityFactory, context, viewUrl)
            ) {
                is WindowWalkResult.Present -> {
                    windowLayers += result.rootLayer
                    layers += result.layers
                    wireframes += result.wireframes
                    pendingPixelCaptures += result.pendingPixelCaptures
                }
                WindowWalkResult.Filtered -> Unit
                WindowWalkResult.Aborted -> {
                    aborted = true
                }
            }
            if (aborted) break
        }

        return if (aborted) null else WindowsWalkAccumulation(windowLayers, layers, wireframes, pendingPixelCaptures)
    }

    private class WindowsWalkAccumulation(
        val windowLayers: List<CapturedLayer>,
        val layers: List<CapturedLayer>,
        val wireframes: List<CapturedWireframe>,
        val pendingPixelCaptures: List<PendingPixelCapture>
    )
}
