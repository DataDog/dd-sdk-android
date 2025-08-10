/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface BatteryInfoProvider {
    fun getBatteryState(): BatteryInfo
}
