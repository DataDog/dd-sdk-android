/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ChecksSdkIntAtLeast
import com.datadog.android.api.InternalLogger
import java.lang.reflect.Method

/** Children in back-to-front painting order, shared by traversal and occlusion for one parent. */
internal data class ViewGroupDrawingOrder(
    val children: List<View>,
    val isReliable: Boolean
)

internal class ViewGroupDrawingOrderResolver(
    private val internalLogger: InternalLogger,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) {
    fun resolve(viewGroup: ViewGroup): ViewGroupDrawingOrder {
        val childCount = viewGroup.childCount
        val children = mutableListOf<View>()
        for (i in 0 until childCount) {
            viewGroup.getChildAt(i)?.let(children::add)
        }
        val ordered = if (children.size == childCount) resolveCustomOrder(viewGroup, children) else null
        if (ordered == null) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { "Unable to resolve child drawing order; retaining all children without occlusion culling" },
                onlyOnce = true
            )
        }
        // Android applies a stable Z sort after custom drawing order. View.z includes translationZ;
        // equal-Z siblings retain their custom (or natural, when disabled) drawing order.
        return ViewGroupDrawingOrder(
            children = (ordered ?: children).sortedBy { it.z },
            isReliable = ordered != null
        )
    }

    // Both reflection and an app-defined drawing-order callback may throw. An unreadable/invalid
    // order must retain every child and disable culling, rather than silently drop visible content.
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall")
    private fun resolveCustomOrder(viewGroup: ViewGroup, children: List<View>): List<View>? {
        if (children.size < 2) return children
        return try {
            // Even the public one-argument getChildDrawingOrder calls the custom callback when
            // ordering is disabled. dispatchDraw checks this protected SDK flag first, so we must too.
            when (ChildDrawingOrderMethods.enabled?.invoke(viewGroup) as? Boolean) {
                true -> {
                    val indices = children.indices.map { position ->
                        if (hasPublicDrawingOrderApi()) {
                            viewGroup.getChildDrawingOrder(position)
                        } else {
                            ChildDrawingOrderMethods.order?.invoke(viewGroup, children.size, position) as? Int ?: -1
                        }
                    }
                    if (indices.toSet().size == children.size && indices.all { it in children.indices }) {
                        indices.map { children[it] }
                    } else {
                        null
                    }
                }
                false -> children
                null -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private fun hasPublicDrawingOrderApi(): Boolean = sdkInt >= Build.VERSION_CODES.Q
}

/** These protected SDK hooks predate API 29. Cache their lookup, but never retain a ViewGroup. */
private object ChildDrawingOrderMethods {
    val enabled: Method? by lazy { sdkMethod("isChildrenDrawingOrderEnabled") }
    val order: Method? by lazy { sdkMethod("getChildDrawingOrder", Integer.TYPE, Integer.TYPE) }

    @Suppress("UnsafeThirdPartyFunctionCall") // reflection failures are handled here and disable culling at the caller.
    private fun sdkMethod(name: String, vararg parameterTypes: Class<*>): Method? = try {
        ViewGroup::class.java.getDeclaredMethod(name, *parameterTypes).also { it.isAccessible = true }
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
