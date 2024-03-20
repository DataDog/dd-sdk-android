/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composer
import androidx.compose.runtime.Composition
import androidx.core.view.children
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionContextHolderClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionContextHolderRefField
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionContextImplClass
import com.datadog.android.sessionreplay.compose.internal.reflection.ComposeReflection.CompositionContextImplComposersField
import com.datadog.android.sessionreplay.compose.internal.reflection.getSafe

internal fun findComposer(view: View): Composer? {
    val directComposer = getViewComposer(view)
    return directComposer ?: if (view is ViewGroup) {
        view.children
            .mapNotNull { findComposer(it) }
            .firstOrNull()
    } else {
        null
    }
}

private val wrappedCompositionTagKey = androidx.compose.ui.R.id.wrapped_composition_tag

/**
 * The composition instance is added as a tag on the compose view by the Jetpack Compose compiler plugin.
 */
private fun getViewComposer(view: View): Composer? {
    var composition = (view.getTag(wrappedCompositionTagKey) as? Composition)
    if (ComposeReflection.WrappedCompositionClass?.isInstance(composition) == true) {
        composition = ComposeReflection.WrappedCompositionOriginalField?.getSafe(composition) as? Composition
    }

    // If the composition is disposed, its hierarchy is invalid and shouldn't be taken into account
    if (composition != null && !composition.isDisposed) {
        if (ComposeReflection.CompositionImplClass?.isInstance(composition) == true) {
            return ComposeReflection.CompositionImplComposerField?.getSafe(composition) as? Composer
        }
    }

    return null
}

internal fun getSubComposers(data: Any?): Iterable<Composer> {
    val compositionContext = if (CompositionContextHolderClass?.isInstance(data) == true) {
        CompositionContextHolderRefField?.getSafe(data)
    } else {
        null
    }

    val composers = if (CompositionContextImplClass?.isInstance(compositionContext) == true) {
        CompositionContextImplComposersField?.getSafe(compositionContext)
    } else {
        null
    }

    @Suppress("UNCHECKED_CAST")
    return (composers as? Iterable<Composer>)?.toList().orEmpty()
}
