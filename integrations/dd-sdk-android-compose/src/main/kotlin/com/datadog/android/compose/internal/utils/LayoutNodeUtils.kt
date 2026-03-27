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
import androidx.compose.ui.semantics.Role
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

    @Suppress("NestedBlockDepth", "CyclomaticComplexMethod")
    fun resolveLayoutNode(node: LayoutNode): TargetNode? {
        return runSafe("resolveLayoutNode") {
            var isClickable = false
            var isScrollable = false
            var datadogTag: String? = null
            var role: Role? = null
            var selected: Boolean? = null
            val customAttributes = mutableMapOf<String, Any?>()
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
                        selected = selected ?: getOrNull(SemanticsProperties.Selected)
                        role = role ?: getOrNull(SemanticsProperties.Role)
                    }
                } else {
                    when (val name = modifier::class.qualifiedName) {
                        CLASS_NAME_SELECTABLE_ELEMENT,
                        CLASS_NAME_CLICKABLE_ELEMENT,
                        CLASS_NAME_COMBINED_CLICKABLE_ELEMENT -> {
                            role = role ?: getRole(modifier, name)
                            isClickable = true
                        }

                        CLASS_NAME_TOGGLEABLE_ELEMENT,
                        CLASS_NAME_TRI_STATE_TOGGLEABLE_ELEMENT -> {
                            isClickable = true
                        }

                        CLASS_NAME_SCROLLING_LAYOUT_ELEMENT,
                        CLASS_NAME_SCROLLABLE_ELEMENT -> {
                            isScrollable = true
                        }
                    }
                }
            }
            selected?.let {
                customAttributes[ACTION_TARGET_SELECTED] = it
            }
            role?.let {
                customAttributes[ACTION_TARGET_ROLE] = it
            }
            datadogTag?.let {
                TargetNode(
                    tag = it,
                    isClickable = isClickable,
                    isScrollable = isScrollable,
                    customAttributes = customAttributes.toMap()
                )
            }
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun getRole(obj: Any, modifierClassName: String): Role? {
        return runSafe("getRole($modifierClassName)") {
            val roleField = obj::class.java.getDeclaredField("role")
            roleField.isAccessible = true
            roleField.get(obj) as? Role
        }
    }

    fun getLayoutNodeBoundsInWindow(node: LayoutNode): Rect? {
        return runSafe("getLayoutNodeBoundsInWindow") {
            node.layoutDelegate.outerCoordinator.coordinates.boundsInWindow()
        } ?: runSafe("getLayoutNodeBoundsInWindow[reflection]") {
            // TODO RUM-13454 Update compose bom and remove this block
            val coordinates = node.getMethod("getLayoutDelegate")
                ?.getMethod("getOuterCoordinator")
                ?.getMethod("getCoordinates")

            @Suppress("UnsafeThirdPartyFunctionCall") // it's okay if exception will be thrown here
            Class.forName("androidx.compose.ui.layout.LayoutCoordinatesKt")
                .getMethod("boundsInWindow", Class.forName("androidx.compose.ui.layout.LayoutCoordinates"))
                .invoke(null, coordinates) as? Rect
        }
    }

    private fun Any.getMethod(prefix: String): Any? {
        return this.javaClass.methods.firstOrNull { it.name == prefix || it.name.startsWith("$prefix$") }
            ?.invoke(this)
    }

    private fun <T> runSafe(callSite: String, action: () -> T): T? {
        try {
            return action()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // We rely on visibility suppression to access internal field,
            // any runtime exception must be caught here.
            (Datadog.getInstance() as? FeatureSdkCore)?.internalLogger?.log(
                level = InternalLogger.Level.WARN,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                messageBuilder = { "LayoutNodeUtils execution failure in $callSite." },
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
        private const val CLASS_NAME_TRI_STATE_TOGGLEABLE_ELEMENT =
            "androidx.compose.foundation.selection.TriStateToggleableElement"
        private const val CLASS_NAME_SCROLLING_LAYOUT_ELEMENT =
            "androidx.compose.foundation.ScrollingLayoutElement"
        private const val CLASS_NAME_SCROLLABLE_ELEMENT =
            "androidx.compose.foundation.gestures.ScrollableElement"
        private const val CLASS_NAME_SELECTABLE_ELEMENT =
            "androidx.compose.foundation.selection.SelectableElement"
    }
}
