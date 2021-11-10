/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor

/**
 * Creates a proxy around click listener, which will report clicks to Datadog.
 *
 * @param targetName Name of the click target.
 * @param attributes additional custom attributes to attach to the action. Attributes can be
 * nested up to 9 levels deep. Keys using more than 9 levels will be sanitized by SDK.
 * @param onClick Click listener.
 */
@ExperimentalTrackingApi
@Composable
fun trackClicks(
    targetName: String,
    attributes: Map<String, Any?> = remember { emptyMap() },
    onClick: () -> Unit
): () -> Unit {
    val onTapState = rememberUpdatedState(newValue = onClick)
    return remember(targetName, attributes) {
        TapActionTracker(targetName, attributes, onTapState)
    }
}

internal class TapActionTracker(
    private val targetName: String,
    private val attributes: Map<String, Any?> = emptyMap(),
    private val onTap: State<() -> Unit>,
    private val rumMonitor: RumMonitor = GlobalRum.get()
) : () -> Unit {
    override fun invoke() {
        rumMonitor.addUserAction(
            RumActionType.TAP,
            targetName,
            attributes + mapOf(
                RumAttributes.ACTION_TARGET_TITLE to targetName
            )
        )
        onTap.value.invoke()
    }
}
