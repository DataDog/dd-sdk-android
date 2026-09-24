/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal class GenerationWorkRegistry {
    // Collections.newSetFromMap throws IllegalArgumentException only for a non-empty map;
    // these maps are freshly constructed and always empty.
    @Suppress("UnsafeThirdPartyFunctionCall")
    private val workTokens = Collections.newSetFromMap(ConcurrentHashMap<CaptureWorkToken, Boolean>())

    @Suppress("UnsafeThirdPartyFunctionCall")
    private val trackedWork = Collections.newSetFromMap(ConcurrentHashMap<CancellableCaptureWork, Boolean>())

    fun createToken(context: CaptureGenerationContext): CaptureWorkToken =
        CaptureWorkToken(context, this).also(workTokens::add)

    fun track(work: CancellableCaptureWork) {
        trackedWork += work
    }

    // ConcurrentHashMap.remove throws NPE only for a null key; `work` is a non-null Kotlin type.
    @Suppress("UnsafeThirdPartyFunctionCall")
    fun release(work: CancellableCaptureWork): Boolean = trackedWork.remove(work)

    fun release(token: CaptureWorkToken) {
        workTokens -= token
    }

    fun invalidateAll() {
        workTokens.forEach(CaptureWorkToken::invalidate)
        workTokens.clear()
        trackedWork.forEach(CancellableCaptureWork::cancel)
        trackedWork.clear()
    }
}
