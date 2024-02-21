/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.reflection

import java.lang.reflect.Field

internal object ComposeReflection {
    val WrappedCompositionClass = Class.forName("androidx.compose.ui.platform.WrappedComposition")
    val WrappedCompositionOriginalField = WrappedCompositionClass.getDeclaredField("original").accessible()

    val CompositionImplClass = Class.forName("androidx.compose.runtime.CompositionImpl")
    val CompositionImplComposerField = CompositionImplClass.getDeclaredField("composer").accessible()

    val CompositionContextHolderClass = Class.forName("androidx.compose.runtime.ComposerImpl\$CompositionContextHolder")
    val CompositionContextHolderRefField = CompositionContextHolderClass.getDeclaredField("ref").accessible()

    val CompositionContextImplClass = Class.forName("androidx.compose.runtime.ComposerImpl\$CompositionContextImpl")
    val CompositionContextImplComposersField = CompositionContextImplClass.getDeclaredField("composers").accessible()

    val AnchorClass = Class.forName("androidx.compose.runtime.Anchor")
    val AnchorLocationField = AnchorClass.getDeclaredField("location").accessible()

    val RecomposeScopeImplClass = Class.forName("androidx.compose.runtime.RecomposeScopeImpl")
    val RecomposeScopeImplBlockField = RecomposeScopeImplClass.getDeclaredField("block").accessible()
}

internal fun Field.accessible(): Field {
    isAccessible = true
    return this
}
