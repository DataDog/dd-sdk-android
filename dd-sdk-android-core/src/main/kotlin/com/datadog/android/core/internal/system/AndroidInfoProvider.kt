/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import com.datadog.android.api.context.DeviceType

internal interface AndroidInfoProvider {

    val deviceName: String

    val deviceBrand: String

    val deviceModel: String

    val deviceType: DeviceType

    val deviceBuildId: String

    val osName: String

    val osMajorVersion: String

    val osVersion: String

    val architecture: String

    val numberOfDisplays: Int?

    val locales: List<String>

    val currentLocale: String

    val timeZone: String

    val logicalCpuCount: Int

    val totalRam: Int?

    val isLowRam: Boolean?
}
