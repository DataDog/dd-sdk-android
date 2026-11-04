/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** Identifies empty AndroidX-owned system-bar children, never the layout's app-content children. */
internal class AndroidXProtectionViewMatcher(
    private val markerReader: (Class<*>) -> Any? = ProtectionViewMarkerReader::read
) {
    // A marker is a plain bootstrap Object, not a Field/Class that retains the optional class loader.
    // A non-null entry also remembers unsupported versions so they do not repeat reflection per frame.
    private val markers = WeakHashMap<Class<*>, Marker>()

    @Suppress("ReturnCount")
    fun isEmptyProtectionView(view: View): Boolean {
        if (view.javaClass != View::class.java) return false
        if (view.isFocused) return false
        if (view.isFocusable || view.isClickable || view.isLongClickable) return false
        if (view.background != null || view.foreground != null) return false
        if (view.isHorizontalScrollBarEnabled || view.isVerticalScrollBarEnabled) return false
        val tag = view.tag ?: return false
        val parentType = view.parent?.javaClass ?: return false
        if (parentType.name != PROTECTION_LAYOUT_CLASS) return false
        val marker = synchronized(markers) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Non-null Class key; optional reflection failures return null.
            markers.getOrPut(parentType) { Marker(markerReader(parentType)) }.value
        }
        return marker != null && tag === marker
    }

    private class Marker(val value: Any?)

    private companion object {
        const val PROTECTION_LAYOUT_CLASS = "androidx.core.view.insets.ProtectionLayout"
    }
}

/** Optional library internals can change or be inaccessible; unknown ownership retains normal capture. */
internal object ProtectionViewMarkerReader {
    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Optional reflection failures are caught below.
    fun read(type: Class<*>): Any? = try {
        val field = type.getDeclaredField("PROTECTION_VIEW")
        if (field.type != Any::class.java || !Modifier.isStatic(field.modifiers) ||
            !Modifier.isFinal(field.modifiers)
        ) {
            null
        } else {
            field.isAccessible = true
            field.get(null)?.takeIf { it.javaClass == Any::class.java }
        }
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: LinkageError) {
        null
    }
}
