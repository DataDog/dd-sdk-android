/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.api.context.DeviceType
import com.datadog.android.core.internal.system.AndroidInfoProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class AndroidInfoProviderForgeryFactory : ForgeryFactory<AndroidInfoProvider> {

    override fun getForgery(forge: Forge): AndroidInfoProvider {
        val deviceName = forge.aString()
        val deviceBrand = forge.aString()
        val deviceModel = forge.aString()
        val deviceType = forge.aValueFrom(DeviceType::class.java)
        val deviceBuildId = forge.aString()
        val osName = forge.aString()
        val osMajorVersion = forge.aSmallInt().toString()
        val osVersion = "${forge.aSmallInt()}.${forge.aSmallInt()}.${forge.aSmallInt()}"
        val architecture = forge.anAlphaNumericalString()
        val numberOfDisplays = forge.aNullable { forge.anInt() }
        val locales = forge.aList { forge.aString() }
        val currentLocale = forge.aString()
        val timeZone = forge.aString()
        val logicalCpuCount = forge.anInt()
        val totalRam = forge.anInt()
        val isLowRam = forge.aBool()

        return object : AndroidInfoProvider {
            override val deviceName = deviceName
            override val deviceBrand = deviceBrand
            override val deviceModel = deviceModel
            override val deviceType = deviceType
            override val deviceBuildId = deviceBuildId
            override val osName = osName
            override val osMajorVersion = osMajorVersion
            override val osVersion = osVersion
            override val architecture = architecture
            override val numberOfDisplays = numberOfDisplays
            override val locales: List<String> = locales
            override val currentLocale: String = currentLocale
            override val timeZone: String = timeZone
            override val logicalCpuCount: Int = logicalCpuCount
            override val totalRam: Int? = totalRam
            override val isLowRam: Boolean? = isLowRam
        }
    }
}
