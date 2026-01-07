/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tests.elmyr

import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class BatteryInfoForgeryFactory : ForgeryFactory<BatteryInfo> {
    override fun getForgery(forge: Forge): BatteryInfo {
        return BatteryInfo(
            batteryLevel = forge.aNullable { aFloat(min = 0.0f, max = 1.0f) },
            lowPowerMode = forge.aNullable { aBool() }
        )
    }
}
