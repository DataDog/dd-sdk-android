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
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.tracking.InteractionPredicate
import com.datadog.android.rum.tracking.NoOpInteractionPredicate
import com.datadog.android.rum.tracking.ViewAttributesProvider
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import java.lang.ref.WeakReference
import kotlin.Exception

@Suppress("TooGenericExceptionCaught")
internal class WindowCallbackWrapper(
    window: Window,
    val sdkCore: SdkCore,
    val wrappedCallback: Window.Callback,
    val gesturesDetector: GesturesDetectorWrapper,
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
                    "Error processing MotionEvent",
                    e
                )
            } finally {
                copy.recycle()
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                "Received null MotionEvent"
            )
        }

        return try {
            wrappedCallback.dispatchTouchEvent(event)
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                "Wrapped callback failed processing MotionEvent",
                e
            )
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
        GlobalRum.get(sdkCore).addAction(
            RumActionType.TAP,
            resolveTargetName(interactionPredicate, item),
            attributes
        )
        return try {
            wrappedCallback.onMenuItemSelected(featureId, item)
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                "Wrapped callback failed processing MenuItem selection",
                e
            )
            EVENT_CONSUMED
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                "Received null KeyEvent"
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
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                "Wrapped callback failed processing KeyEvent",
                e
            )
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
            GlobalRum.get(sdkCore).addAction(RumActionType.CLICK, targetName, attributes)
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
        GlobalRum.get(sdkCore).addAction(RumActionType.BACK, targetName, emptyMap())
    }

    // endregion

    companion object {
        const val BACK_DEFAULT_TARGET_NAME: String = "back"

        const val EVENT_CONSUMED: Boolean = true
    }
}
