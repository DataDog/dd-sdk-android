/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

internal abstract class ThreadSafeReceiver : BroadcastReceiver() {

    val isRegistered = AtomicBoolean(false)

    // We suppress the warning here as this method is not available on all Android versions
    @SuppressLint("WrongConstant", "UnspecifiedRegisterReceiverFlag")
    fun registerReceiver(
        context: Context,
        filter: IntentFilter
    ): Intent? {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(this, filter, RECEIVER_NOT_EXPORTED_COMPAT)
        } else {
            context.registerReceiver(this, filter)
        }
        isRegistered.set(true)
        return intent
    }

    fun unregisterReceiver(context: Context) {
        if (isRegistered.compareAndSet(true, false)) {
            context.unregisterReceiver(this)
        }
    }

    companion object {
        internal const val RECEIVER_NOT_EXPORTED_COMPAT: Int = 0x4
    }
}
