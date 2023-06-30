/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.core.persistence.Serializer

internal class NetworkInfoSerializer :
    Serializer<NetworkInfo> {

    override fun serialize(model: NetworkInfo): String {
        return model.toJson().asJsonObject.toString()
    }
}
