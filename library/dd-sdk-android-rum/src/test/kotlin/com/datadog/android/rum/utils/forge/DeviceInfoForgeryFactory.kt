/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.v2.api.context.DeviceInfo
import com.datadog.android.v2.api.context.DeviceType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

// TODO RUMM-2949 Share forgeries/test configurations between modules
class DeviceInfoForgeryFactory : ForgeryFactory<DeviceInfo> {
    override fun getForgery(forge: Forge): DeviceInfo {
        return DeviceInfo(
            deviceName = forge.anAlphabeticalString(),
            deviceBrand = forge.anAlphabeticalString(),
            deviceModel = forge.anAlphabeticalString(),
            deviceType = forge.aValueFrom(DeviceType::class.java),
            deviceBuildId = forge.anAlphaNumericalString(),
            osName = forge.aString(),
            osVersion = forge.aString(),
            osMajorVersion = forge.aString(),
            architecture = forge.aString()
        )
    }
}