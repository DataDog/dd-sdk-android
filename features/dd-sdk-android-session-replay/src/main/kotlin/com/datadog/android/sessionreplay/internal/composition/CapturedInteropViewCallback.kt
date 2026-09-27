/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult

/**
 * The seam a future Compose semantics-tree walker (not yet built) can call into when it hits an
 * embedded native `AndroidView`, to reuse this module's native mapper registry - mirroring legacy's
 * one-directional `InteropViewCallback`. No reverse (native-calls-into-Compose) callback exists yet
 * since nothing needs it: a bare `ComposeView` encountered during native traversal falls through to
 * the generic [com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper],
 * matching current production behavior.
 */
internal fun interface CapturedInteropViewCallback {
    fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult
}
