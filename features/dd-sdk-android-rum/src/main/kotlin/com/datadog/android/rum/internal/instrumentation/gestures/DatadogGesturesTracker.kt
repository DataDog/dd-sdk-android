/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.content.Context
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference

internal class DatadogGesturesTracker(
    internal val targetAttributesProviders: Array<ViewAttributesProvider>,
    internal val interactionPredicate: InteractionPredicate,
    private val internalLogger: InternalLogger
) : GesturesTracker {

    // region GesturesTracker

    override fun startTracking(
        window: Window?,
        context: Context,
        sdkCore: SdkCore
    ) {
        if (window == null) {
            return
        }

        val toWrap = window.callback ?: NoOpWindowCallback()
        val gesturesDetector = generateGestureDetector(context, window, sdkCore)

        window.callback = WindowCallbackWrapper(
            window,
            sdkCore,
            toWrap,
            gesturesDetector,
            interactionPredicate,
            targetAttributesProviders = targetAttributesProviders,
            internalLogger = internalLogger
        )
    }

    override fun stopTracking(window: Window?, context: Context) {
        if (window == null) {
            return
        }

        val currentCallback = window.callback
        if (currentCallback is WindowCallbackWrapper) {
            if (currentCallback.wrappedCallback !is NoOpWindowCallback) {
                window.callback = currentCallback.wrappedCallback
            } else {
                window.callback = null
            }
        }
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DatadogGesturesTracker

        if (!targetAttributesProviders.contentEquals(other.targetAttributesProviders)) return false

        if (interactionPredicate.javaClass != other.interactionPredicate.javaClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 17
        val multiplier = 31
        result += result * multiplier + targetAttributesProviders.contentHashCode()
        result += result * multiplier + interactionPredicate.javaClass.hashCode()
        return result
    }

    override fun toString(): String {
        return "DatadogGesturesTracker(${targetAttributesProviders.joinToString()})"
    }

    // endregion

    // region Internal

    internal fun generateGestureDetector(
        context: Context,
        window: Window,
        sdkCore: SdkCore
    ): GesturesDetectorWrapper {
        return GesturesDetectorWrapper(
            context,
            GesturesListener(
                sdkCore,
                WeakReference(window),
                targetAttributesProviders,
                interactionPredicate,
                WeakReference(context),
                internalLogger
            )
        )
    }

    // endregion
}
