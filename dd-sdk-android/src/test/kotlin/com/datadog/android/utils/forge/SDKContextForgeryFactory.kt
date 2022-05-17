/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.context.ApplicationInfo
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.ProcessInfo
import com.datadog.android.v2.api.context.SDKContext
import com.datadog.android.v2.api.context.TimeInfo
import com.datadog.android.v2.api.context.UserInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class SDKContextForgeryFactory : ForgeryFactory<SDKContext> {
    /**
     * TODO RUMM-000 implement all nested class factories
     */
    override fun getForgery(forge: Forge): SDKContext {
        return SDKContext(
            TimeInfo(forge.aLong(), forge.aLong()),
            ApplicationInfo(
                forge.anAlphabeticalString(),
                forge.anAlphabeticalString(),
                forge.anInt()
            ),
            ProcessInfo(forge.aBool(), forge.anInt()),
            NetworkInfo(forge.aValueFrom(NetworkInfo.Connectivity::class.java), null),
            UserInfo(null, null, null, emptyMap()),
            forge.aValueFrom(TrackingConsent::class.java),
            emptyMap()
        )
    }
}
