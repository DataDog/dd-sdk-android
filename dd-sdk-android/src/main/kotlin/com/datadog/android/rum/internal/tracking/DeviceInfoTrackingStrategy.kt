/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.datadog.android.rum.tracking.ActivityLifecycleTrackingStrategy
import java.util.concurrent.Executor

internal class DeviceInfoTrackingStrategy : ActivityLifecycleTrackingStrategy(),
    DeviceInfoProvider {

    var deviceInfo = DeviceInfo()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window?.callback?.let {
            activity.window.callback = DeviceInfoWindowCallbackWrapper(
                it,
                activity
            ) {
                deviceInfo = it
            }
        }
        super.onActivityCreated(activity, savedInstanceState)
    }

    override fun onActivityDestroyed(activity: Activity) {
        (activity.window.callback as? DeviceInfoWindowCallbackWrapper)?.let {
            it.detach(activity)
        }
        super.onActivityDestroyed(activity)
    }

    companion object {
        val MAIN_THREAD_EXECUTOR: Executor =
            object : Executor {
                private val handler =
                    Handler(Looper.getMainLooper())

                override fun execute(command: Runnable) {
                    handler.post(command)
                }
            }
    }

    override fun getLastDeviceStateInfo(): DeviceInfo {
        return deviceInfo
    }
}