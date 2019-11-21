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
            forge.anElementFrom(
                NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED,
                NetworkInfo.Connectivity.NETWORK_WIFI,
                NetworkInfo.Connectivity.NETWORK_2G,
                NetworkInfo.Connectivity.NETWORK_3G,
                NetworkInfo.Connectivity.NETWORK_4G,
                NetworkInfo.Connectivity.NETWORK_5G,
                NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
                NetworkInfo.Connectivity.NETWORK_OTHER
            ),
            forge.anAlphabeticalString(),
            forge.anInt(-1, 10000)
        )
    }
}
