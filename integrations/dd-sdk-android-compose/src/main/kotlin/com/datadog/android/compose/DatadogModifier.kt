/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Adds Datadog-specific semantic information to the layout node for the Session Replay feature.
 *
 * This modifier ensures that the component is included in the semantics tree, allowing
 * Session Replay to identify and interpret it correctly during a replay session.
 *
 * @param name The name of the component to be displayed in the semantics tree.
 * @param isImage Set to `true` if the component represents an image. This helps Session Replay
 *                attempt to resolve and capture the image content appropriately.
 */
fun Modifier.datadog(name: String, isImage: Boolean = false): Modifier {
    return this.semantics {
        this.datadog = name
        if (isImage) {
            this[SemanticsProperties.Role] = Role.Image
        }
    }
}

internal val DatadogSemanticsPropertyKey: SemanticsPropertyKey<String> = SemanticsPropertyKey(
    name = "_dd_semantics",
    mergePolicy = { parentValue, _ ->
        parentValue
    }
)

private var SemanticsPropertyReceiver.datadog by DatadogSemanticsPropertyKey
