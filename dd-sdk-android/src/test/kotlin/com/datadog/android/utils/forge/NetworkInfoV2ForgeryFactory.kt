/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.v2.api.context.CarrierInfo
import com.datadog.android.v2.api.context.NetworkInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class NetworkInfoV2ForgeryFactory : ForgeryFactory<NetworkInfo> {
    override fun getForgery(forge: Forge): NetworkInfo {
        return NetworkInfo(
            connectivity = forge.aValueFrom(NetworkInfo.Connectivity::class.java),
            carrier = forge.aNullable {
                CarrierInfo(
                    technology = forge.aNullable { forge.aString() },
                    carrierName = forge.aNullable { forge.aString() }
                )
            }
        )
    }
}
