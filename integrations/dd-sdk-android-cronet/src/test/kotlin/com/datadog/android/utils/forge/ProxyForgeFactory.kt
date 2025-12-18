/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils.forge

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.chromium.net.Proxy

internal class ProxyForgeFactory : ForgeryFactory<Proxy> {
    override fun getForgery(forge: Forge) = Proxy(
        forge.anElementFrom(Proxy.HTTP, Proxy.HTTPS),
        forge.aString(),
        forge.anInt(min = 1, max = 65535),
        object : Proxy.Callback() {

            override fun onBeforeTunnelRequest() = emptyList<Map.Entry<String, String>>()

            override fun onTunnelHeadersReceived(
                responseHeaders: List<Map.Entry<String, String>>,
                statusCode: Int
            ) = false
        }
    )
}
