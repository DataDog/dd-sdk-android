/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ModifierInfo

/** Reads only the inner text layout's coordinates; no editable text or selection is reflected. */
@Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // Optional internals; unknown runtimes keep the fallback.
internal fun editableTextCoordinates(modifiers: List<ModifierInfo>): LayoutCoordinates? {
    val modifier = modifiers.firstOrNull {
        it.modifier.javaClass.name == "androidx.compose.foundation.text.input.internal.CoreTextFieldSemanticsModifier"
    }?.modifier ?: return null
    return try {
        val state = modifier.javaClass.getDeclaredField("state").apply { isAccessible = true }.get(modifier)
            ?: return null
        val getter = state.javaClass.methods.firstOrNull {
            it.parameterTypes.isEmpty() && it.name.substringBefore('$') == "getLayoutCoordinates"
        } ?: return null
        (getter.invoke(state) as? LayoutCoordinates)?.takeIf { it.isAttached }
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
