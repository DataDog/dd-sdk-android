/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import androidx.annotation.MainThread
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * The real [CapturedSnapshotProducer] for the plain Android View hierarchy - the workstream-3
 * implementation of the extension point [SnapshotCaptureOrchestrator] drives every generation.
 * Builds a fresh [CapturedIdentityFactory] per call (this workstream only produces full snapshots;
 * an identity factory persisting across generations for incremental diffing is a later workstream's
 * concern), walks every currently active window via [AndroidWindowTraversal], and assembles them
 * under one synthetic screen root in [ActiveWindowSource.currentWindows] order (already z-ordered).
 *
 * Windows are walked one at a time via [AndroidWindowTraversal.traverseWindow], which can itself
 * yield mid-window - [continueWindow] resumes that exact window before moving on to the next, so a
 * pause never skips or re-starts a window's own progress.
 */
internal class AndroidCapturedSnapshotProducer(
    private val windowSource: ActiveWindowSource,
    private val scopeProvider: RumViewScopeProvider,
    private val timeProvider: TimeProvider,
    private val traversal: AndroidWindowTraversal,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
) : CapturedSnapshotProducer {

    @MainThread
    @Suppress("ReturnCount")
    override fun capture(context: CaptureGenerationContext): CaptureStep<CapturedFullSnapshot?> {
        val rumViewScope = scopeProvider.currentScope() ?: return CaptureStep.Done(null)
        val identityFactory = DefaultCapturedIdentityFactory(rumViewScope.scope)
        val windows = windowSource.currentWindows()
        if (windows.isEmpty()) {
            touchPrivacyManager.updateCurrentTouchOverrideAreas()
            return CaptureStep.Done(null)
        }
        return walkFrom(windows, 0, identityFactory, context, WindowsWalkAccumulation(), rumViewScope)
    }

    /**
     * [index] reaching the end of [windows] means every window was walked without aborting -
     * whether or not any of them actually contributed a layer (all could be [WindowWalkResult.Filtered])
     * - so this always builds a snapshot rather than falling back to null the way an empty [windows]
     * itself does in [capture].
     */
    @MainThread
    private fun walkFrom(
        windows: List<View>,
        index: Int,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        accumulation: WindowsWalkAccumulation,
        rumViewScope: CapturedRumViewScope
    ): CaptureStep<CapturedFullSnapshot?> {
        if (index >= windows.size) {
            touchPrivacyManager.updateCurrentTouchOverrideAreas()
            return CaptureStep.Done(accumulation.buildSnapshot(identityFactory, timeProvider, rumViewScope))
        }
        val window = windows[index]
        val windowIdentity = identityFactory.window(viewIdentifierResolver.resolveViewId(window).toString())
        return continueWindow(
            traversal.traverseWindow(window, windowIdentity, identityFactory, context),
            windows,
            index,
            identityFactory,
            context,
            accumulation,
            rumViewScope
        )
    }

    @MainThread
    private fun continueWindow(
        step: CaptureStep<WindowWalkResult>,
        windows: List<View>,
        index: Int,
        identityFactory: CapturedIdentityFactory,
        context: CaptureGenerationContext,
        accumulation: WindowsWalkAccumulation,
        rumViewScope: CapturedRumViewScope
    ): CaptureStep<CapturedFullSnapshot?> = when (step) {
        is CaptureStep.Yielded -> CaptureStep.Yielded {
            continueWindow(step.resume(), windows, index, identityFactory, context, accumulation, rumViewScope)
        }
        is CaptureStep.Done -> when (val result = step.value) {
            is WindowWalkResult.Present -> {
                accumulation.absorb(result)
                walkFrom(windows, index + 1, identityFactory, context, accumulation, rumViewScope)
            }
            WindowWalkResult.Filtered -> walkFrom(
                windows,
                index + 1,
                identityFactory,
                context,
                accumulation,
                rumViewScope
            )
            WindowWalkResult.Aborted -> {
                touchPrivacyManager.updateCurrentTouchOverrideAreas()
                CaptureStep.Done(null)
            }
        }
    }

    private class WindowsWalkAccumulation {
        private val windowLayers = mutableListOf<CapturedLayer>()
        private val layers = mutableListOf<CapturedLayer>()
        private val wireframes = mutableListOf<CapturedWireframe>()

        fun absorb(result: WindowWalkResult.Present) {
            windowLayers += result.rootLayer
            layers += result.layers
            wireframes += result.wireframes
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
