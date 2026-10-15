/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import androidx.annotation.MainThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.putTouchOverrideArea
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * The real [CapturedSnapshotProducer] for the plain Android View hierarchy - the workstream-3
 * implementation of the extension point [SnapshotCaptureOrchestrator] drives every generation.
 * Builds a fresh [CapturedIdentityFactory] per call (this workstream only produces full snapshots;
 * an identity factory persisting across generations for incremental diffing is a later workstream's
 * concern), walks active main-thread-owned windows via [AndroidWindowTraversal], and assembles them
 * under one synthetic screen root in [ActiveWindowSource.currentWindows] order (already z-ordered).
 *
 * All hierarchy reads complete in one main-thread pass. Each window checks the shared generation
 * deadline, and only a complete capture publishes its touch overrides.
 */
internal class AndroidCapturedSnapshotProducer(
    private val windowSource: ActiveWindowSource,
    private val scopeProvider: RumViewScopeProvider,
    private val timeProvider: TimeProvider,
    private val traversal: AndroidWindowTraversal,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver,
    private val isMainThreadWindow: (View) -> Boolean = ::isAttachedToMainLooper
) : CapturedSnapshotProducer {

    @MainThread
    @Suppress("ReturnCount") // Reject missing scope, stale windows and aborted traversal before publication.
    override fun capture(context: CaptureGenerationContext): CapturedFullSnapshot? {
        val rumViewScope = scopeProvider.currentScope()
        if (rumViewScope == null) {
            if (context.shouldContinue()) {
                windowSource.clearTouchPrivacy()
                touchPrivacyManager.replaceCurrentTouchOverrideAreas(emptyMap())
            }
            return null
        }
        val identityFactory = DefaultCapturedIdentityFactory(rumViewScope.scope)
        val windowSnapshot = windowSource.snapshot()
        val windows = windowSnapshot.windows.filter(isMainThreadWindow)
        if (windows.isEmpty()) {
            if (context.shouldContinue() && windowSource.isCurrent(windowSnapshot)) {
                windowSource.clearTouchPrivacy()
                touchPrivacyManager.replaceCurrentTouchOverrideAreas(emptyMap())
            }
            return null
        }
        val accumulation = WindowsWalkAccumulation()
        for (window in windows) {
            if (!canContinueCapture(context, rumViewScope, windowSnapshot) || !isMainThreadWindow(window)) return null
            val windowIdentity = identityFactory.window(viewIdentifierResolver.resolveViewId(window).toString())
            when (val result = traversal.traverseWindow(window, windowIdentity, identityFactory, context)) {
                is WindowWalkResult.Present -> {
                    accumulation.absorb(window, result)
                    accumulation.capturedWindows += window
                }
                WindowWalkResult.Filtered -> Unit
                WindowWalkResult.Aborted -> return null
            }
        }
        val snapshot = accumulation.buildSnapshot(identityFactory, timeProvider, rumViewScope)
        if (!canContinueCapture(context, rumViewScope, windowSnapshot)) return null
        // No partial areas enter the legacy manager's staging state, including on cancellation.
        windowSource.publishTouchPrivacy(
            windowSnapshot,
            accumulation.capturedWindows,
            rumViewScope.scope,
            accumulation.touchOverridesByWindow
        ) {
            touchPrivacyManager.replaceCurrentTouchOverrideAreas(accumulation.touchOverrideAreas)
        }
        return snapshot
    }

    private fun canContinueCapture(
        context: CaptureGenerationContext,
        rumViewScope: CapturedRumViewScope,
        windowSnapshot: ActiveWindowSource.Snapshot
    ): Boolean = context.shouldContinue() &&
        scopeProvider.currentScope()?.scope == rumViewScope.scope &&
        windowSource.isCurrent(windowSnapshot)

    private class WindowsWalkAccumulation {
        private val windowLayers = mutableListOf<CapturedLayer>()
        private val layers = mutableListOf<CapturedLayer>()
        private val wireframes = mutableListOf<CapturedWireframe>()
        val touchOverrideAreas = mutableMapOf<Rect, TouchPrivacy>()
        val capturedWindows = mutableListOf<View>()
        val touchOverridesByWindow = mutableMapOf<View, Map<Rect, TouchPrivacy>>()

        fun absorb(window: View, result: WindowWalkResult.Present) {
            windowLayers += result.rootLayer
            layers += result.layers
            wireframes += result.wireframes
            result.touchOverrideAreas.forEach { (bounds, privacy) ->
                touchOverrideAreas.putTouchOverrideArea(bounds, privacy)
            }
            touchOverridesByWindow[window] = result.touchOverrideAreas
        }

        fun buildSnapshot(
            identityFactory: CapturedIdentityFactory,
            timeProvider: TimeProvider,
            rumViewScope: CapturedRumViewScope
        ): CapturedFullSnapshot {
            val root = CapturedLayer(
                identity = identityFactory.screenRoot(),
                kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
                // Every window could have come back Filtered, leaving this empty even though the
                // caller's own window list wasn't - falls back rather than crashing on that.
                bounds = windowLayers.firstOrNull()?.bounds ?: CapturedBounds(0, 0, 0, 0),
                children = windowLayers.map { layer -> CapturedChild.Layer(layer.identity) }
            )
            return CapturedFullSnapshot(
                timestamp = timeProvider.getDeviceTimestampMillis() + rumViewScope.viewTimeOffsetMs,
                scope = rumViewScope.scope,
                root = root,
                layers = layers,
                wireframes = wireframes
            )
        }
    }
}
