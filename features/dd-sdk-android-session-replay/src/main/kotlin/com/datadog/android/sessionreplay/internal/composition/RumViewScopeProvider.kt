/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider

/** The current RUM view's identity scope and time offset, or null when there is no active view. */
internal data class CapturedRumViewScope(
    val scope: RumViewIdentityScope,
    val viewTimeOffsetMs: Long
)

/**
 * Isolates the traversal layer from [com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext]
 * details it doesn't need beyond the identity scope a capture must be tagged with and the offset
 * used to timestamp it.
 */
internal fun interface RumViewScopeProvider {
    fun currentScope(): CapturedRumViewScope?
}

internal class DefaultRumViewScopeProvider(
    private val rumContextProvider: RumContextProvider
) : RumViewScopeProvider {
    override fun currentScope(): CapturedRumViewScope? {
        val rumContext = rumContextProvider.getRumContext()
        if (rumContext.isNotValid()) return null
        return CapturedRumViewScope(
            scope = RumViewIdentityScope(rumContext.viewId),
            viewTimeOffsetMs = rumContext.viewTimeOffsetMs
        )
    }
}
