/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils.forge

import android.util.Pair
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.chromium.net.Proxy
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.util.concurrent.Executor

internal class ProxyForgeFactory : ForgeryFactory<Proxy> {
    override fun getForgery(forge: Forge) = Proxy.createHttpProxy(
        forge.anElementFrom(Proxy.SCHEME_HTTP, Proxy.SCHEME_HTTPS),
        forge.aString(),
        forge.anInt(min = 1, max = 65535),
        mock<Executor> {
            on { execute(any()) } doAnswer { invocation ->
                invocation.getArgument<() -> Unit>(0).invoke()
            }
        },
        object : Proxy.HttpConnectCallback() {

            override fun onBeforeRequest(request: Proxy.HttpConnectCallback.Request) {
                request.proceed(emptyList<Pair<String, String>>())
            }

            override fun onResponseReceived(
                responseHeaders: List<Pair<String, String>>,
                statusCode: Int
            ): Int = RESPONSE_ACTION_PROCEED
        }
    )
}
