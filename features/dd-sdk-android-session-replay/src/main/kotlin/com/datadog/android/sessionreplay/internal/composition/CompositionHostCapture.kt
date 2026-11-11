/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext

/**
 * Calls the extension synchronously and returns native handoffs only after decomposition succeeds.
 * Pending copies belong to each call; neither live Views nor copying callbacks are retained.
 */
internal class CompositionHostCapture(
    private val decomposer: CompositionHostDecomposer,
    private val internalLogger: InternalLogger,
    private val textAndInputPrivacy: TextAndInputPrivacy
) {
    @UiThread
    fun canDecompose(view: View): Boolean = invokeDecomposer(false) { decomposer.canDecompose(view) }

    @UiThread
    fun readTouchOverrideAreas(view: View, shouldContinue: () -> Boolean): Map<Rect, TouchPrivacy>? =
        invokeDecomposer(null, TOUCH_PRIVACY_REFRESH_FAILED_MESSAGE) {
            decomposer.readTouchOverrideAreas(view, shouldContinue)
        }

    @UiThread
    fun capture(
        view: View,
        mappingContext: CapturedMappingContext,
        shouldContinue: () -> Boolean,
        copyNativeSubtree: (View, CapturedIdentity) -> WindowCopyResult.Present?
    ): Result? {
        val pendingNativeCopies = mutableListOf<WindowCopyResult.Present>()
        val request = CompositionHostDecomposeRequest(
            identityFactory = mappingContext.identityFactory,
            hostIdentity = mappingContext.ownerIdentity,
            screenDensity = mappingContext.screenDensity,
            hostBounds = mappingContext.bounds,
            textAndInputPrivacy = resolveTextPrivacy(view, shouldContinue),
            internalLogger = internalLogger,
            shouldContinue = shouldContinue,
            nativeViewHandoff = { interopView, childIdentity ->
                val copy = if (shouldContinue()) copyNativeSubtree(interopView, childIdentity) else null
                if (copy == null) {
                    false
                } else {
                    pendingNativeCopies += copy
                    true
                }
            }
        )
        val result = if (shouldContinue()) {
            invokeDecomposer<CompositionHostDecomposeResult?>(null) { decomposer.decompose(view, request) }
        } else {
            null
        }
        return if (result != null && shouldContinue()) Result(result, pendingNativeCopies) else null
    }

    @UiThread
    private fun resolveTextPrivacy(view: View, shouldContinue: () -> Boolean): TextAndInputPrivacy {
        var current: View? = view
        while (current != null && shouldContinue()) {
            val override = current.getTag(R.id.datadog_text_and_input_privacy) as? String
            if (override != null) {
                try {
                    return TextAndInputPrivacy.valueOf(override)
                } catch (error: IllegalArgumentException) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        { INVALID_TEXT_PRIVACY_OVERRIDE_MESSAGE },
                        throwable = error,
                        onlyOnce = true
                    )
                }
            }
            current = current.parent as? View
        }
        return textAndInputPrivacy
    }

    @Suppress("TooGenericExceptionCaught") // Optional extension linkage failures must not escape onto the UI thread.
    private inline fun <T> invokeDecomposer(
        fallback: T,
        failureMessage: String = HOST_DECOMPOSITION_FAILED_MESSAGE,
        block: () -> T
    ): T = try {
        block()
    } catch (throwable: Throwable) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { failureMessage },
            throwable = throwable,
            onlyOnce = true
        )
        fallback
    }

    class Result(
        val decomposition: CompositionHostDecomposeResult,
        val nativeCopies: List<WindowCopyResult.Present>
    )

    private companion object {
        const val HOST_DECOMPOSITION_FAILED_MESSAGE =
            "Composition host decomposition failed; falling back to native capture"
        const val TOUCH_PRIVACY_REFRESH_FAILED_MESSAGE =
            "Composition touch privacy refresh failed; skipping touch recording"
        const val INVALID_TEXT_PRIVACY_OVERRIDE_MESSAGE =
            "Invalid text privacy override; inheriting the parent policy"
    }
}
