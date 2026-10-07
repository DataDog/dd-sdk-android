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
 * Maps a view at the native/Compose boundary. [AndroidWindowTraversal] uses an optional callback
 * to hand Compose hosts to a Compose mapper without traversing their native implementation children.
 * A Compose walker can also use this interface to map embedded native AndroidViews.
 */
internal fun interface CapturedInteropViewCallback {
    fun map(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult
}
