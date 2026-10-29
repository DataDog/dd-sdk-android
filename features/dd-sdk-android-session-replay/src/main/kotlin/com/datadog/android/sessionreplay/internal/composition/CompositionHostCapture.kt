/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext

/**
 * Calls the extension synchronously and returns native handoffs only after decomposition succeeds.
 * Pending copies belong to each call; neither live Views nor copying callbacks are retained.
 */
internal class CompositionHostCapture(
    private val decomposer: CompositionHostDecomposer,
    private val internalLogger: InternalLogger
) {
    @UiThread
    fun canDecompose(view: View): Boolean = invokeDecomposer(false) { decomposer.canDecompose(view) }

    @UiThread
    fun capture(
        view: View,
        mappingContext: CapturedMappingContext,
        copyNativeSubtree: (View, CapturedIdentity) -> WindowCopyResult.Present?
    ): Result? {
        val pendingNativeCopies = mutableListOf<WindowCopyResult.Present>()
        val request = CompositionHostDecomposeRequest(
            identityFactory = mappingContext.identityFactory,
            hostIdentity = mappingContext.ownerIdentity,
            screenDensity = mappingContext.screenDensity,
            hostBounds = mappingContext.bounds,
            nativeViewHandoff = { interopView, childIdentity ->
                val copy = copyNativeSubtree(interopView, childIdentity)
                if (copy == null) {
                    false
                } else {
                    pendingNativeCopies += copy
                    true
                }
            }
        )
        val result = invokeDecomposer<CompositionHostDecomposeResult?>(null) {
            decomposer.decompose(view, request)
        } ?: return null
        return Result(result, pendingNativeCopies)
    }

    @Suppress("TooGenericExceptionCaught") // Optional extension linkage failures must not escape onto the UI thread.
    private inline fun <T> invokeDecomposer(fallback: T, block: () -> T): T = try {
        block()
    } catch (throwable: Throwable) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Composition host decomposition failed; falling back to native capture" },
            throwable = throwable,
            onlyOnce = true
        )
        fallback
    }

    class Result(
        val decomposition: CompositionHostDecomposeResult,
        val nativeCopies: List<WindowCopyResult.Present>
    )
}
