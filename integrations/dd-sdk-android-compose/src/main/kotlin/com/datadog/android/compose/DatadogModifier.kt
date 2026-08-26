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
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
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
    return this then DatadogSemanticsElement(name, isImage, autoInstrumented = false)
}

/**
 * This is the internal function reserved to Datadog Kotlin Compiler Plugin for auto instrumentation,
 * with telemetry to indicate that the auto-instrumentation is used instead of manual instrumentation.
 */
internal fun Modifier.instrumentedDatadog(name: String, isImage: Boolean): Modifier {
    return this then DatadogSemanticsElement(name, isImage, autoInstrumented = true)
}

private class DatadogSemanticsElement(
    private val name: String,
    private val isImage: Boolean,
    private val autoInstrumented: Boolean
) : ModifierNodeElement<DatadogSemanticsNode>(), SemanticsModifier {

    override val semanticsConfiguration: SemanticsConfiguration
        get() = SemanticsConfiguration().apply {
            datadog = name
            if (isImage) {
                this[SemanticsProperties.Role] = Role.Image
            }
        }

    override fun create(): DatadogSemanticsNode =
        DatadogSemanticsNode(name, isImage, autoInstrumented)

    override fun update(node: DatadogSemanticsNode) {
        node.update(name, isImage)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatadogSemanticsElement) return false
        return name == other.name &&
            isImage == other.isImage &&
            autoInstrumented == other.autoInstrumented
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = HASH_MULTIPLIER * result + isImage.hashCode()
        result = HASH_MULTIPLIER * result + autoInstrumented.hashCode()
        return result
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "datadog"
        properties["name"] = this@DatadogSemanticsElement.name
        properties["isImage"] = isImage
    }

    private companion object {
        private const val HASH_MULTIPLIER = 31
    }
}

private class DatadogSemanticsNode(
    private var name: String,
    private var isImage: Boolean,
    private val autoInstrumented: Boolean
) : Modifier.Node(), SemanticsModifierNode {

    override fun onAttach() {
        sendTelemetry(autoInstrumented = autoInstrumented, InstrumentationType.Semantics)
    }

    fun update(name: String, isImage: Boolean) {
        this.name = name
        this.isImage = isImage
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
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
