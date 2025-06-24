/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures

import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import java.lang.ref.WeakReference
import kotlin.Exception

@Suppress("TooGenericExceptionCaught")
internal class WindowCallbackWrapper(
    window: Window,
    val sdkCore: SdkCore,
    val wrappedCallback: Window.Callback,
    private val gesturesDetector: GesturesDetectorWrapper,
    val interactionPredicate: InteractionPredicate = NoOpInteractionPredicate(),
    val copyEvent: (MotionEvent) -> MotionEvent = {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        MotionEvent.obtain(it)
    },
    val targetAttributesProviders: Array<ViewAttributesProvider> = emptyArray(),
    val internalLogger: InternalLogger
) : Window.Callback by wrappedCallback {

    internal val windowReference = WeakReference(window)

    // region Window.Callback

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            // we copy it and delegate it to the gesture detector for analysis
            @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
            val copy = copyEvent(event)
            try {
                gesturesDetector.onTouchEvent(copy)
            } catch (e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { "Error processing MotionEvent" },
                    e
                )
            } finally {
                copy.recycle()
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Received null MotionEvent" }
            )
        }

        return try {
            wrappedCallback.dispatchTouchEvent(event)
        } catch (e: NullPointerException) {
            logOrRethrowWrappedCallbackException(e)
            EVENT_CONSUMED
        }
    }

    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
        val resourceId = windowReference.get()?.context.resourceIdName(item.itemId)
        val attributes = mutableMapOf<String, Any?>(
            RumAttributes.ACTION_TARGET_CLASS_NAME to item.javaClass.canonicalName,
            RumAttributes.ACTION_TARGET_RESOURCE_ID to resourceId,
            RumAttributes.ACTION_TARGET_TITLE to item.title
        )
        GlobalRumMonitor.get(sdkCore).addAction(
            RumActionType.TAP,
            resolveTargetName(interactionPredicate, item),
            attributes
        )
        return try {
            wrappedCallback.onMenuItemSelected(featureId, item)
        } catch (e: NullPointerException) {
            logOrRethrowWrappedCallbackException(e)
            EVENT_CONSUMED
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { "Received null KeyEvent" }
            )
        } else if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP
        ) {
            handleBackEvent(event)
        } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
            event.action == KeyEvent.ACTION_UP
        ) {
            handleRemoteControlActionEvent()
        }
        return try {
            wrappedCallback.dispatchKeyEvent(event)
        } catch (e: NullPointerException) {
            logOrRethrowWrappedCallbackException(e)
            EVENT_CONSUMED
        }
    }

    // endregion

    // region Internal

    private fun handleRemoteControlActionEvent() {
        val window = windowReference.get()
        window?.currentFocus?.let {
            val resourceIdName = window.context.resourceIdName(it.id)
            val attributes = mutableMapOf<String, Any?>(
                RumAttributes.ACTION_TARGET_CLASS_NAME to it.targetClassName(),
                RumAttributes.ACTION_TARGET_RESOURCE_ID to resourceIdName
            )
            targetAttributesProviders.forEach { provider ->
                provider.extractAttributes(it, attributes)
            }
            val targetName = resolveTargetName(interactionPredicate, it)
            GlobalRumMonitor.get(sdkCore).addAction(RumActionType.CLICK, targetName, attributes)
        }
    }

    private fun handleBackEvent(event: KeyEvent) {
        val customTargetName = interactionPredicate.getTargetName(event)
        val targetName = if (customTargetName.isNullOrEmpty()) {
            // We will keep using the default target name as we are not
            // sending the ACTION_TARGET_CLASSNAME attribute in this case and backend will not
            // be able to resolve the targetName.
            BACK_DEFAULT_TARGET_NAME
        } else {
            customTargetName
        }
        GlobalRumMonitor.get(sdkCore).addAction(RumActionType.BACK, targetName)
    }

    private fun logOrRethrowWrappedCallbackException(e: NullPointerException) {
        // When calling delegate callback, we may have something like
        // java.lang.NullPointerException: Parameter specified as non-null is null:
        // method xxx, parameter xxx
        // This happens because Kotlin delegate expects non-null value incorrectly inferring
        // non-null type from Java interface definition (seems to be solved in Kotlin 1.8 though)
        if (e.message?.contains("Parameter specified as non-null is null") == true) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Wrapped Window.Callback failed processing event" },
                e
            )
        } else {
            @Suppress("ThrowingInternalException") // we need to let client exception to propagate
            throw e
        }
    }

    // endregion

    companion object {
        const val BACK_DEFAULT_TARGET_NAME: String = "back"

        const val EVENT_CONSUMED: Boolean = true
    }
}
