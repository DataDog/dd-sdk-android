/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.rum.internal.domain.scope.RumRawEvent

internal object RumDebugObject {

    fun isTargetEvent(event: RumRawEvent) =
        event is RumRawEvent.AddCustomTiming ||
                event is RumRawEvent.StartView ||
                event is RumRawEvent.StopView
}