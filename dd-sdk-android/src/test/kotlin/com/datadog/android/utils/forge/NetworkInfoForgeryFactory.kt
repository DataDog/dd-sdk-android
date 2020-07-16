/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.net.info.NetworkInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class NetworkInfoForgeryFactory : ForgeryFactory<NetworkInfo> {

    override fun getForgery(forge: Forge): NetworkInfo {
        return NetworkInfo(
            connectivity = forge.aValueFrom(NetworkInfo.Connectivity::class.java),
            carrierName = forge.anElementFrom(
                forge.anAlphabeticalString(),
                forge.aWhitespaceString(),
                null
            ),
            carrierId = if (forge.aBool()) forge.anInt(0, 10000) else -1,
            upKbps = forge.anInt(0, Int.MAX_VALUE),
            downKbps = forge.anInt(0, Int.MAX_VALUE),
            strength = forge.anInt(-100, -30), // dBm for wifi signal
            cellularTechnology = forge.aNullable { anAlphabeticalString() }
        )
    }
}
