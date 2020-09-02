/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.content.Context
import android.view.Window
import androidx.core.util.Consumer
import androidx.window.DeviceState
import androidx.window.WindowLayoutInfo
import androidx.window.WindowManager

internal class DeviceInfoWindowCallbackWrapper(
    val wrapped: Window.Callback,
    val context: Context,
    val callback: (DeviceInfo) -> Unit
) :
    Window.Callback by wrapped {

    var deviceInfo = DeviceInfo()
    var windowManager: WindowManager? = null

    private val deviceStateCallback: Consumer<DeviceState> by lazy {
        Consumer<DeviceState> {
            it?.let {
                deviceInfo = deviceInfo.copy(state = DeviceInfo.Posture.fromPosture(it.posture))
                callback(deviceInfo)

            }
        }
    }

    private val windowLayoutCallback: Consumer<WindowLayoutInfo> by lazy {
        Consumer<WindowLayoutInfo> {
            it?.let {
                // For testing purposes we're just going to get the first display for now
                it.displayFeatures.getOrNull(0)?.let { displayFeature ->
                    deviceInfo =
                        deviceInfo.copy(displayType = DeviceInfo.DisplayType.fromType(displayFeature.type))
                    callback(deviceInfo)

                }
            }
        }
    }


    override fun onAttachedToWindow() {
        val windowManager = WindowManager(context, null)
        updateDeviceInfo(windowManager)
        windowManager.registerDeviceStateChangeCallback(
            DeviceInfoTrackingStrategy.MAIN_THREAD_EXECUTOR,
            deviceStateCallback
        )
        windowManager.registerLayoutChangeCallback(
            DeviceInfoTrackingStrategy.MAIN_THREAD_EXECUTOR,
            windowLayoutCallback
        )
        windowManager.windowLayoutInfo
        this.windowManager = windowManager
        wrapped.onAttachedToWindow()
    }

    private fun updateDeviceInfo(windowManager: WindowManager) {
        // For testing purposes we're just going to get the first display for now
        val displayFeature = windowManager.windowLayoutInfo.displayFeatures.getOrNull(0)
        deviceInfo =
            DeviceInfo(
                state = DeviceInfo.Posture.fromPosture(
                    windowManager.deviceState.posture
                ),
                displayType = if (displayFeature != null) DeviceInfo.DisplayType.fromType(
                    displayFeature.type
                ) else null
            )
        callback(deviceInfo)
    }

    override fun onDetachedFromWindow() {
        wrapped.onDetachedFromWindow()
        unregisterCallbacks()
    }

    fun detach(activity: Activity) {
        activity.window.callback = wrapped
    }

    private fun unregisterCallbacks() {
        windowManager?.unregisterDeviceStateChangeCallback(deviceStateCallback)
        windowManager?.unregisterLayoutChangeCallback(windowLayoutCallback)
    }
}