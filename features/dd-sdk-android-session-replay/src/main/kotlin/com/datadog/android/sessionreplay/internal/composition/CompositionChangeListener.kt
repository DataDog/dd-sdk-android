/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View

/** Reports which decor views drew or were (re)discovered; owns no traversal, enrichment, queue, or generation state. */
internal fun interface CompositionChangeListener {
    fun onWindowsChanged(windows: List<View>)
}
