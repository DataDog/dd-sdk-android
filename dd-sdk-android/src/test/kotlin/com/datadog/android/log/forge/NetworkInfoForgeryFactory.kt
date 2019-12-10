/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.forge

import com.datadog.android.log.internal.net.NetworkInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class NetworkInfoForgeryFactory : ForgeryFactory<NetworkInfo> {

    override fun getForgery(forge: Forge): NetworkInfo {
        return NetworkInfo(
            forge.aValueFrom(NetworkInfo.Connectivity::class.java),
            forge.anElementFrom(
                forge.anAlphabeticalString(),
                forge.aWhitespaceString(),
                null
            ),
            if (forge.aBool()) forge.anInt(0, 10000) else -1
        )
    }
}
