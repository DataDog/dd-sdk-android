/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.executor.GlideExecutor.newDiskCacheBuilder
import com.bumptech.glide.load.engine.executor.GlideExecutor.newSourceBuilder
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.datadog.android.DatadogEventListener
import com.datadog.android.DatadogInterceptor
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Provides a basic implementation of [AppGlideModule] already set up to send relevant information
 * to Datadog.
 *
 * This sets up an OkHttp based downloader that will send Traces and RUM Resource events.
 * Also any Glide related error (Disk cache, source transformation, …) will be sent as RUM Errors.
 * @param firstPartyHosts the list of first party hosts.
 * Requests made to a URL with any one of these hosts (or any subdomain) will:
 * - be considered a first party RUM Resource and categorised as such in your RUM dashboard;
 * - be wrapped in a Span and have trace id injected to get a full flame-graph in APM.
 * If no host provided the interceptor won't trace any OkHttp [Request], nor propagate tracing
 * information to the backend, but RUM Resource events will still be sent for each request.
 */
open class DatadogGlideModule
@JvmOverloads constructor(
    private val firstPartyHosts: List<String> = emptyList()
) : AppGlideModule() {

    // region AppGlideModule

    /** @inheritdoc */
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = getClientBuilder().build()
        val factory = OkHttpUrlLoader.Factory(client)

        registry.replace(GlideUrl::class.java, InputStream::class.java, factory)
    }

    /** @inheritdoc */
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setDiskCacheExecutor(
            newDiskCacheBuilder()
                .setUncaughtThrowableStrategy(DatadogRUMUncaughtThrowableStrategy("Disk Cache"))
                .build()
        )

        builder.setSourceExecutor(
            newSourceBuilder()
                .setUncaughtThrowableStrategy(DatadogRUMUncaughtThrowableStrategy("Source"))
                .build()
        )
    }

    // endregion

    // region DatadogGlideModule

    /**
     * Creates the [OkHttpClient.Builder].
     * The default implementation returns a builder already setup with a [DatadogInterceptor]
     * and [DatadogEventListener.Factory].
     * @return the builder for the [OkHttpClient] to be used by Glide
     */
    open fun getClientBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .addInterceptor((DatadogInterceptor(firstPartyHosts)))
            .eventListenerFactory(DatadogEventListener.Factory())
    }

    // endregion
}
