/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import androidx.annotation.UiThread
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult

/**
 * Maps a supported view at a framework boundary. The integration owns typed host recognition, so
 * [AndroidWindowTraversal] needs no framework dependency or class-name checks. A Compose integration
 * can recognize its hosts with a typed check; a Compose walker can delegate embedded native Views.
 *
 * Return null for an unsupported view to continue native mapping and traversal. Any non-null result,
 * including [CapturedViewMapperResult.None], handles the whole host and suppresses native child
 * traversal even when it emits no wireframes. Returned values must not retain live UI objects.
 */
internal fun interface CapturedInteropViewCallback {
    @UiThread
    fun tryMap(view: View, mappingContext: CapturedMappingContext): CapturedViewMapperResult?
}
