/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import androidx.compose.runtime.State
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor

internal class TapActionTracker(
    private val targetName: String,
    private val attributes: Map<String, Any?> = emptyMap(),
    private val onTap: State<() -> Unit>,
    private val rumMonitor: RumMonitor
) : () -> Unit {
    override fun invoke() {
        rumMonitor.addAction(
            RumActionType.TAP,
            targetName,
            attributes + mapOf(
                RumAttributes.ACTION_TARGET_TITLE to targetName
            )
        )
        // that is user code, not ours
        @Suppress("UnsafeThirdPartyFunctionCall")
        onTap.value.invoke()
    }
}
