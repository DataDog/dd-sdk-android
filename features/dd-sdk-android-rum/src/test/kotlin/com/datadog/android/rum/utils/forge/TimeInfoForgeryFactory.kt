/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.api.context.TimeInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

// TODO RUMM-2949 Share forgeries/test configurations between modules
class TimeInfoForgeryFactory : ForgeryFactory<TimeInfo> {
    override fun getForgery(forge: Forge): TimeInfo {
        return TimeInfo(
            deviceTimeNs = forge.aLong(min = 0),
            serverTimeNs = forge.aLong(min = 0),
            serverTimeOffsetNs = forge.aLong(),
            serverTimeOffsetMs = forge.aLong()
        )
    }
}
