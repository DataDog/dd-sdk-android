/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.DeviceType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

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
            architecture = forge.aString(),
            numberOfDisplays = forge.aNullable { forge.anInt() },
            localeInfo = forge.getForgery(),
            logicalCpuCount = forge.anInt(),
            totalRam = forge.aNullable { anInt() },
            isLowRam = forge.aNullable { aBool() }
        )
    }
}
