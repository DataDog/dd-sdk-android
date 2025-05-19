/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import com.datadog.android.api.context.DeviceType

internal class NoOpAndroidInfoProvider : AndroidInfoProvider {
    override val deviceName: String = ""
    override val deviceBrand: String = ""
    override val deviceModel: String = ""
    override val deviceType: DeviceType = DeviceType.MOBILE
    override val deviceBuildId: String = ""
    override val osName: String = ""
    override val osMajorVersion: String = ""
    override val osVersion: String = ""
    override val architecture: String = ""
    override val numberOfDisplays: Int? = null
}
