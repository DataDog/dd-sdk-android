/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.atomic.AtomicBoolean

internal abstract class ThreadSafeReceiver : BroadcastReceiver() {

    val isRegistered = AtomicBoolean(false)

    fun registerReceiver(
        context: Context,
        filter: IntentFilter
    ): Intent? {
        val intent = context.registerReceiver(this, filter)
        isRegistered.set(true)
        return intent
    }

    fun unregisterReceiver(context: Context) {
        if (isRegistered.compareAndSet(true, false)) {
            context.unregisterReceiver(this)
        }
    }
}
