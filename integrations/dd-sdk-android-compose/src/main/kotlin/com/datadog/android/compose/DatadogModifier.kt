/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("PackageNameVisibility")

package com.datadog.android.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.datadog.android.compose.internal.InstrumentationType
import com.datadog.android.compose.internal.sendTelemetry

/**
 * Adds Datadog-specific semantic information to the layout node for the Session Replay feature.
 *
 * This modifier ensures that the component is included in the semantics tree, allowing
 * Session Replay to identify and interpret it correctly during recording.
 *
 * @param name The name of the component to be displayed in the semantics tree.
 * @param isImage Set to `true` if the component represents an image. This helps Session Replay
 *                attempt to resolve and capture the image content appropriately.
 */
fun Modifier.datadog(name: String, isImage: Boolean = false): Modifier {
    sendTelemetry(autoInstrumented = false, InstrumentationType.Semantics)
    return this.datadogSemantics(name, isImage)
}

/**
 * This is the internal function reserved to Datadog Kotlin Compiler Plugin for auto instrumentation,
 * with telemetry to indicate that the auto-instrumentation is used instead of manual instrumentation.
 *
 * @param name The name of the component, identical in meaning and value to what manual
 *             instrumentation would set. Unaffected by [elementPath].
 * @param isImage Set to `true` if the component represents an image.
 * @param elementPath A path unique to this call site at compile time (e.g. "MyScreen/Button#1"),
 *                     intended for future stable element identification (e.g. heatmaps). This is
 *                     additive: it is stored under a separate key and never replaces or alters
 *                     [name] or the existing `_dd_semantics` tag read by RUM action tracking today.
 *                     Defaults to `null` (no value stored) when omitted
 */
internal fun Modifier.instrumentedDatadog(name: String, isImage: Boolean, elementPath: String? = null): Modifier {
    sendTelemetry(autoInstrumented = true, InstrumentationType.Semantics)
    return this.datadogSemantics(name, isImage, elementPath)
}

private fun Modifier.datadogSemantics(name: String, isImage: Boolean, elementPath: String? = null): Modifier {
    return this.semantics {
        this.datadog = name
        if (isImage) {
            this[SemanticsProperties.Role] = Role.Image
        }
        if (elementPath != null) {
            this[DatadogElementPathPropertyKey] = elementPath
        }
    }
}

internal val DatadogSemanticsPropertyKey: SemanticsPropertyKey<String> = SemanticsPropertyKey(
    name = "_dd_semantics",
    mergePolicy = { parentValue, _ ->
        parentValue
    }
)

internal val DatadogElementPathPropertyKey: SemanticsPropertyKey<String> = SemanticsPropertyKey(
    name = "_dd_element_path",
    mergePolicy = { parentValue, _ ->
        parentValue
    }
)

private var SemanticsPropertyReceiver.datadog by DatadogSemanticsPropertyKey
