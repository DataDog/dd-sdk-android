/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("PackageNameVisibility")

package com.datadog.android.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
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
    return this.then(DatadogSemanticsElement(name, isImage))
}

/**
 * This is the internal function reserved to Datadog Kotlin Compiler Plugin for auto instrumentation,
 * with telemetry to indicate that the auto-instrumentation is used instead of manual instrumentation.
 */
internal fun Modifier.instrumentedDatadog(name: String, isImage: Boolean): Modifier {
    sendTelemetry(autoInstrumented = true, InstrumentationType.Semantics)
    return this.then(DatadogSemanticsElement(name, isImage))
}

internal val DatadogSemanticsPropertyKey: SemanticsPropertyKey<String> = SemanticsPropertyKey(
    name = "_dd_semantics",
    mergePolicy = { parentValue, _ ->
        parentValue
    }
)

internal var SemanticsPropertyReceiver.datadog by DatadogSemanticsPropertyKey

/**
 * A custom [ModifierNodeElement] that provides Datadog semantics without participating in
 * layout measurement. This replaces the previous `Modifier.semantics {}` approach to avoid
 * interference with layout constraint propagation in components like `SubcomposeLayout`
 * (e.g., Coil's `SubcomposeAsyncImage`) inside `LazyRow`/`LazyColumn`.
 *
 * By implementing only [SemanticsModifierNode] (and not `LayoutModifierNode`), this modifier
 * node is explicitly excluded from the layout measurement chain, ensuring it never modifies
 * constraints passed to child composables.
 */
internal class DatadogSemanticsElement(private val name: String, private val isImage: Boolean) :
    ModifierNodeElement<DatadogSemanticsNode>() {
    override fun create(): DatadogSemanticsNode = DatadogSemanticsNode(name, isImage)

    override fun update(node: DatadogSemanticsNode) {
        node.name = name
        node.isImage = isImage
    }

    override fun InspectorInfo.inspectableProperties() {
        this.properties["name"] = name
        this.properties["isImage"] = isImage
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatadogSemanticsElement) return false
        return name == other.name && isImage == other.isImage
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + isImage.hashCode()
        return result
    }
}

/**
 * A [SemanticsModifierNode] that attaches Datadog component metadata to the semantics tree.
 * This node does NOT implement `LayoutModifierNode`, so it is never consulted during
 * layout measurement and cannot modify constraints.
 */
internal class DatadogSemanticsNode(var name: String, var isImage: Boolean) :
    Modifier.Node(),
    SemanticsModifierNode {
    override val shouldMergeDescendantSemantics: Boolean get() = false
    override val shouldClearDescendantSemantics: Boolean get() = false

    override fun SemanticsPropertyReceiver.applySemantics() {
        this.datadog = name
        if (isImage) {
            this[SemanticsProperties.Role] = Role.Image
        }
    }
}
