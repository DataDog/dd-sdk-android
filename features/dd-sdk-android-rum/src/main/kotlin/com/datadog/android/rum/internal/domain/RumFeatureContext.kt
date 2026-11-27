/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext

internal fun MutableMap<String, Any?>.setRumContext(context: RumContext?) {
    clear()
    if (context == null) return

    this[FeatureContextKeys.RUM_CONTEXT] = context
}
