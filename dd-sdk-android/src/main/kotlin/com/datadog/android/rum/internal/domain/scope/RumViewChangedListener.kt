/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import java.lang.ref.Reference

internal data class RumViewInfo(
    val keyRef: Reference<Any>,
    val name: String,
    val attributes: Map<String, Any?>,
    val isActive: Boolean
)

internal interface RumViewChangedListener {
    fun onViewChanged(viewInfo: RumViewInfo)
}
