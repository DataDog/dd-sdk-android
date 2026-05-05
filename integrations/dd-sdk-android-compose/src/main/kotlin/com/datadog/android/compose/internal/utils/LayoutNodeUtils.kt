/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose.internal.utils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
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
import java.lang.reflect.Method

internal class LayoutNodeUtils {

    private val methodResolver = MethodResolver()

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

    fun getLayoutNodeBoundsInWindow(node: LayoutNode): Rect? = when (methodResolver.state) {
        MethodResolver.State.UNKNOWN -> {
            getLayoutNodeBoundsInWindowInternal(node) ?: getLayoutNodeBoundsInWindowReflection(node)
        }

        MethodResolver.State.MANGLING_FAILED -> getLayoutNodeBoundsInWindowReflection(node)
        MethodResolver.State.REFLECTION_FAILED -> getLayoutNodeBoundsInWindowInternal(node)
    }

    internal fun getLayoutNodeBoundsInWindowInternal(node: LayoutNode): Rect? = runSafe(
        "getLayoutNodeBoundsInWindow"
    ) { node.layoutDelegate.outerCoordinator.coordinates.boundsInWindow() }

    internal fun getLayoutNodeBoundsInWindowReflection(node: LayoutNode) = runSafe(
        "getLayoutNodeBoundsInWindow[reflection]"
    ) {
        // TODO RUM-13454 Update compose bom and remove this method
        methodResolver.state = MethodResolver.State.MANGLING_FAILED
        val coordinates = node.invokeWithReflection("getLayoutDelegate")
            ?.invokeWithReflection("getOuterCoordinator")
            ?.invokeWithReflection("getCoordinates") as? LayoutCoordinates
        coordinates?.boundsInWindow()
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // runSafe in the caller swallows any Throwable
    private fun Any.invokeWithReflection(prefix: String): Any? {
        if (methodResolver.state == MethodResolver.State.REFLECTION_FAILED) return null
        return methodResolver
            .findMethod(javaClass, prefix)
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

    internal class MethodResolver {
        // RUM-15813: Kotlin internal accessors in androidx.compose.ui are JVM-mangled with
        // a module suffix (plain / "$ui" / "$ui_release"). The mangling is stable per class,
        // but NOT necessarily the same across classes in the reflection chain: e.g. on Compose
        // UI 1.10 LayoutNode.getLayoutDelegate$ui is mangled while LayoutNodeLayoutDelegate
        // exposes getOuterCoordinator without a suffix. We therefore cache the resolved suffix
        // per owner-class rather than once globally.
        //
        enum class State {
            UNKNOWN, // — Try internal mangling resolution, if fails - reflection.
            MANGLING_FAILED, // Mangling resolution failed - allowing reflection attempt
            REFLECTION_FAILED // Reflection resolution failed - switching back to mangling resolution (even if it fails)
        }

        var state: State = State.UNKNOWN
            set(value) {
                if (value.ordinal > field.ordinal) {
                    field = value
                }
            }

        val classPrefixMethodsCache: MutableMap<Class<*>, MutableMap<String, Method?>> = mutableMapOf()

        fun findMethod(klass: Class<*>, prefix: String): Method? =
            classPrefixMethodsCache
                .resolveMethod(klass, prefix)
                .also { if (it == null) state = State.REFLECTION_FAILED }
    }

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

        // Empty suffix covers public accessors; "$ui" and "$ui_release" cover Kotlin-internal
        // accessors in the androidx.compose.ui module (the exact suffix depends on build
        // configuration). Ordered by likelihood — public first, release build second.
        private val SUPPORTED_MANGLING_SUFFIXES = listOf("", "\$ui_release", "\$ui")

        private fun MutableMap<Class<*>, MutableMap<String, Method?>>.resolveMethod(
            klass: Class<*>,
            methodPrefix: String
        ): Method? {
            val klassCache: MutableMap<String, Method?> = getOrPut(klass) { mutableMapOf() }

            if (!klassCache.containsKey(methodPrefix)) {
                klassCache[methodPrefix] = searchManglings(klass, methodPrefix)
            }

            return klassCache[methodPrefix]
        }

        private fun searchManglings(klass: Class<*>, prefix: String): Method? = SUPPORTED_MANGLING_SUFFIXES
            .firstNotNullOfOrNull {
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // NoSuchMethodException is expected here
                    klass.getMethod("$prefix$it")
                } catch (@Suppress("SwallowedException") _: NoSuchMethodException) {
                    null
                }
            }
    }
}
