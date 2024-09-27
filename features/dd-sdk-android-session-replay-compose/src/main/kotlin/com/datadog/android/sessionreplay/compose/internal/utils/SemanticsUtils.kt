/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import android.view.View
import androidx.compose.runtime.Composition
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionField
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe

internal class SemanticsUtils {

    internal fun findRootSemanticsNode(view: View): SemanticsNode? {
        val composition = CompositionField?.getSafe(view) as? Composition
        if (ComposeReflection.WrappedCompositionClass?.isInstance(composition) == true) {
            val owner = ComposeReflection.OwnerField?.getSafe(composition)
            if (ComposeReflection.AndroidComposeViewClass?.isInstance(owner) == true) {
                val semanticsOwner = ComposeReflection.SemanticsOwner?.getSafe(owner) as? SemanticsOwner
                val rootNode = semanticsOwner?.unmergedRootSemanticsNode
                return rootNode
            }
        }
        return null
    }
}
