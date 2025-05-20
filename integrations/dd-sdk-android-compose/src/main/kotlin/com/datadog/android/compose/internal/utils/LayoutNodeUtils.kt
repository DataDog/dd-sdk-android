/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose.internal.utils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.compose.DatadogSemanticsPropertyKey
import com.datadog.android.rum.RumAttributes.ACTION_TARGET_ROLE
import com.datadog.android.rum.RumAttributes.ACTION_TARGET_SELECTED

internal class LayoutNodeUtils {

    @Suppress("NestedBlockDepth")
    fun resolveLayoutNode(node: LayoutNode): TargetNode? {
        return runSafe {
            var isClickable = false
            var isScrollable = false
            var datadogTag: String? = null
            for (info in node.getModifierInfo()) {
                val modifier = info.modifier
                if (modifier is SemanticsModifier) {
                    with(modifier.semanticsConfiguration) {
                        if (contains(SemanticsActions.OnClick)) {
                            isClickable = true
                        }
                        if (contains(SemanticsActions.ScrollBy)) {
                            isScrollable = true
                        }
                        getOrNull(DatadogSemanticsPropertyKey)?.let {
                            datadogTag = it
                        }
                    }
                } else {
                    val className = modifier::class.qualifiedName
                    if (className == CLASS_NAME_CLICKABLE_ELEMENT ||
                        className == CLASS_NAME_COMBINED_CLICKABLE_ELEMENT ||
                        className == CLASS_NAME_TOGGLEABLE_ELEMENT
                    ) {
                        isClickable = true
                    } else if (className == CLASS_NAME_SCROLLING_LAYOUT_ELEMENT ||
                        className == CLASS_NAME_SCROLLABLE_ELEMENT
                    ) {
                        isScrollable = true
                    }
                }
            }

            datadogTag?.let {
                TargetNode(
                    tag = it,
                    isClickable = isClickable,
                    isScrollable = isScrollable,
                    customAttributes = resolveCustomAttributes(node)
                )
            }
        }
    }

    private fun resolveCustomAttributes(node: LayoutNode): Map<String, Any?> {
        return node.collapsedSemantics?.let { configuration ->
            val selected = configuration.getOrNull(SemanticsProperties.Selected)
            val role = configuration.getOrNull(SemanticsProperties.Role)
            mapOf(
                ACTION_TARGET_SELECTED to selected,
                ACTION_TARGET_ROLE to role
            )
        }.orEmpty()
    }

    fun getLayoutNodeBoundsInWindow(node: LayoutNode): Rect? {
        return runSafe {
            node.layoutDelegate.outerCoordinator.coordinates.boundsInWindow()
        }
    }

    private fun <T> runSafe(action: () -> T): T? {
        try {
            return action()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // We rely on visibility suppression to access internal field,
            // any runtime exception must be caught here.
            (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { "LayoutNodeUtils execution failure." },
                throwable = e,
                onlyOnce = true
            )
        }
        return null
    }

    data class TargetNode(
        val tag: String,
        val isScrollable: Boolean,
        val isClickable: Boolean,
        val customAttributes: Map<String, Any?> = mapOf()
    )

    companion object {
        private const val CLASS_NAME_CLICKABLE_ELEMENT =
            "androidx.compose.foundation.ClickableElement"
        private const val CLASS_NAME_COMBINED_CLICKABLE_ELEMENT =
            "androidx.compose.foundation.CombinedClickableElement"
        private const val CLASS_NAME_TOGGLEABLE_ELEMENT =
            "androidx.compose.foundation.selection.ToggleableElement"
        private const val CLASS_NAME_SCROLLING_LAYOUT_ELEMENT =
            "androidx.compose.foundation.ScrollingLayoutElement"
        private const val CLASS_NAME_SCROLLABLE_ELEMENT =
            "androidx.compose.foundation.gestures.ScrollableElement"
    }
}
