/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import androidx.annotation.MainThread
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.putTouchOverrideArea
import com.datadog.android.sessionreplay.utils.DefaultViewIdentifierResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * Copies all supported windows in one UI turn; final tree construction belongs to the worker.
 * Retains one identity factory per RUM view so native, Compose, and screen-root identities remain
 * stable across generations for incremental diffing.
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
    private var retainedIdentityFactory: DefaultCapturedIdentityFactory? = null

    @MainThread
    @Suppress("ReturnCount")
    override fun capture(context: CaptureGenerationContext): CapturedSnapshotInput? {
        val rumViewScope = scopeProvider.currentScope()
        if (rumViewScope == null) {
            if (context.shouldContinue()) {
                windowSource.clearTouchPrivacy()
                touchPrivacyManager.replaceCurrentTouchOverrideAreas(emptyMap())
            }
            return null
        }
        val windowSnapshot = windowSource.snapshot()
        val windows = windowSnapshot.windows.filter(isMainThreadWindow)
        if (windows.isEmpty()) {
            if (context.shouldContinue() && windowSource.isCurrent(windowSnapshot)) {
                windowSource.clearTouchPrivacy()
                touchPrivacyManager.replaceCurrentTouchOverrideAreas(emptyMap())
            }
            return null
        }
        val identityFactory = identityFactoryFor(rumViewScope.scope)
        val copies = mutableListOf<CapturedNativeWindow>()
        val capturedWindows = mutableListOf<View>()
        val touchOverrideAreas = mutableMapOf<Rect, TouchPrivacy>()
        val touchOverridesByWindow = mutableMapOf<View, Map<Rect, TouchPrivacy>>()
        val pendingPixelCaptures = mutableListOf<PendingPixelCapture>()
        for (window in windows) {
            if (!canContinueCapture(context, rumViewScope, windowSnapshot) || !isMainThreadWindow(window)) {
                return null
            }
            val identity = identityFactory.window(viewIdentifierResolver.resolveViewId(window).toString())
            when (
                val result = traversal.traverseWindow(
                    window,
                    identity,
                    identityFactory,
                    context,
                    rumViewScope.viewUrl
                )
            ) {
                is WindowCopyResult.Present -> {
                    copies += result.window
                    capturedWindows += window
                    pendingPixelCaptures += result.window.pendingPixelCaptures
                    result.touchOverrideAreas.forEach { (bounds, privacy) ->
                        touchOverrideAreas.putTouchOverrideArea(bounds, privacy)
                    }
                    touchOverridesByWindow[window] = result.touchOverrideAreas
                }
                WindowCopyResult.Filtered -> Unit
                WindowCopyResult.Aborted -> return null
            }
        }
        val snapshot = CapturedNativeSnapshot(
            timeProvider.getDeviceTimestampMillis() + rumViewScope.viewTimeOffsetMs,
            rumViewScope.scope,
            copies,
            pendingPixelCaptures,
            identityFactory
        )
        if (!canContinueCapture(context, rumViewScope, windowSnapshot) || windows.any { !isMainThreadWindow(it) }) {
            return null
        }
        // Only a complete copy publishes touch overrides; abandoned copies leave no staging state.
        windowSource.publishTouchPrivacy(windowSnapshot, capturedWindows, rumViewScope.scope, touchOverridesByWindow) {
            touchPrivacyManager.replaceCurrentTouchOverrideAreas(touchOverrideAreas)
        }
        return snapshot
    }

    private fun identityFactoryFor(scope: RumViewIdentityScope): DefaultCapturedIdentityFactory {
        retainedIdentityFactory?.takeIf { it.scope == scope }?.let { return it }
        return DefaultCapturedIdentityFactory(scope).also { retainedIdentityFactory = it }
    }

    private fun canContinueCapture(
        context: CaptureGenerationContext,
        rumViewScope: CapturedRumViewScope,
        windowSnapshot: ActiveWindowSource.Snapshot
    ): Boolean = context.shouldContinue() &&
        scopeProvider.currentScope()?.scope == rumViewScope.scope &&
        windowSource.isCurrent(windowSnapshot)
}
