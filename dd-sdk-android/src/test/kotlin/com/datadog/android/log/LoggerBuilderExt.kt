/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.net.NetworkInfoProvider

internal fun Logger.Builder.withNetworkInfoProvider(provider: NetworkInfoProvider): Logger.Builder {
    val method =
        Logger.Builder::class.java
            .getDeclaredMethod("overrideNetworkInfoProvider", NetworkInfoProvider::class.java)

    method.isAccessible = true
    method.invoke(this, provider)
    method.isAccessible = false

    return this
}

internal fun Logger.Builder.withLogStrategy(strategy: LogStrategy): Logger.Builder {
    val method = Logger.Builder::class.java
        .getDeclaredMethod("overrideLogStrategy", LogStrategy::class.java)

    method.isAccessible = true
    method.invoke(this, strategy)
    method.isAccessible = false

    return this
}

internal fun Logger.Builder.withUserAgent(userAgent: String): Logger.Builder {
    val method = Logger.Builder::class.java
        .getDeclaredMethod("overrideUserAgent", String::class.java)

    method.isAccessible = true
    method.invoke(this, userAgent)
    method.isAccessible = false

    return this
}
