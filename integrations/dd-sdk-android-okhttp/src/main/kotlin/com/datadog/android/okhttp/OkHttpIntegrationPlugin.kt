/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.okhttp

import com.datadog.android.okhttp.internal.ApmInstrumentationOkHttpAdapter
import com.datadog.android.okhttp.internal.CompositeEventListener
import com.datadog.android.okhttp.internal.RequestTracingStateRegistry
import com.datadog.android.okhttp.internal.RumInstrumentationOkHttpAdapter
import com.datadog.android.okhttp.internal.RumInstrumentationTimingsCounter
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.rum.internal.net.RumNetworkInstrumentation
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.trace.internal.DatadogTracingToolkit
import okhttp3.EventListener
import okhttp3.OkHttpClient

/**
 * Configures Datadog network instrumentation on this [OkHttpClient.Builder].
 * Returns a [OkHttpIntegrationPlugin] that can be further configured before calling
 * [OkHttpIntegrationPlugin.build].
 *
 * @param apmInstrumentationConfiguration optional APM tracing configuration. When provided, trace spans
 * will be created for HTTP requests and tracing headers will be injected for first-party hosts.
 * @param rumInstrumentationConfiguration optional RUM configuration. When provided, HTTP requests
 * will be automatically tracked as RUM resources with timing information.
 */
@ExperimentalTraceApi
@ExperimentalRumApi
fun OkHttpClient.Builder.configureDatadogInstrumentation(
    apmInstrumentationConfiguration: ApmNetworkInstrumentationConfiguration?,
    rumInstrumentationConfiguration: RumNetworkInstrumentationConfiguration?
) = OkHttpIntegrationPlugin(this, rumInstrumentationConfiguration, apmInstrumentationConfiguration)

/**
 * Plugin that wraps an [OkHttpClient.Builder] with Datadog RUM and APM instrumentation.
 * Use [OkHttpClient.Builder.configureDatadogInstrumentation] to create an instance,
 * then call [build] to produce an instrumented [OkHttpClient].
 */
@ExperimentalRumApi
@ExperimentalTraceApi
class OkHttpIntegrationPlugin internal constructor(
    private val delegate: OkHttpClient.Builder,
    private val rumInstrumentationConfiguration: RumNetworkInstrumentationConfiguration?,
    private val apmInstrumentationConfiguration: ApmNetworkInstrumentationConfiguration?
) {
    private var userEventListenerFactory: EventListener.Factory? = null

    /**
     * Configure a single client scoped listener that will receive all analytic events for this
     * client.
     *
     * @see EventListener for semantics and restrictions on listener implementations.
     */
    fun eventListener(eventListener: EventListener) = apply {
        @Suppress("UnsafeThirdPartyFunctionCall") // OkHttp safe method
        this.userEventListenerFactory = EventListener.Factory { eventListener }
    }

    /**
     * Configure a factory to provide per-call scoped listeners that will receive analytic events
     * for this client.
     *
     * @see EventListener for semantics and restrictions on listener implementations.
     */
    fun eventListenerFactory(eventListenerFactory: EventListener.Factory) = apply {
        this.userEventListenerFactory = eventListenerFactory
    }

    /**
     * Builds the [OkHttpClient] with Datadog instrumentation applied.
     * @return an instrumented [OkHttpClient] instance.
     */
    fun build(): OkHttpClient {
        val rumNetworkInstrumentation = rumInstrumentationConfiguration?.let {
            _RumInternalProxy.createRumNetworkInstrumentation(
                OKHTTP_NETWORK_INSTRUMENTATION_NAME,
                it
            )
        }

        val apmInstrumentation = apmInstrumentationConfiguration
            ?.takeUnless { it.isHeaderPropagationOnly() }
            ?.let { configuration ->
                DatadogTracingToolkit.createApmNetworkInstrumentation(
                    OKHTTP_NETWORK_INSTRUMENTATION_NAME,
                    configuration
                )
            }

        val distributedTracingInstrumentation = rumNetworkInstrumentation?.let {
            apmInstrumentationConfiguration
                ?.copy()
                ?.let { configuration ->
                    DatadogTracingToolkit.createApmNetworkInstrumentation(
                        OKHTTP_NETWORK_INSTRUMENTATION_NAME,
                        configuration
                            .setTraceScope(ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS)
                            .setTraceOrigin(ORIGIN_RUM, replace = false)
                    )
                }
        }

        val registry = RequestTracingStateRegistry()
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        delegate.eventListenerFactory(
            createEventListenerFactory(rumNetworkInstrumentation, registry)
        )

        if (rumNetworkInstrumentation != null) {
            @Suppress("UnsafeThirdPartyFunctionCall") // IllegalArgumentException cannot happen here
            delegate.addInterceptor(RumInstrumentationOkHttpAdapter(rumNetworkInstrumentation, registry))
            if (distributedTracingInstrumentation != null) {
                delegate.addInterceptor(
                    ApmInstrumentationOkHttpAdapter(distributedTracingInstrumentation, registry)
                )
            }
        }

        if (apmInstrumentation != null) {
            @Suppress("UnsafeThirdPartyFunctionCall") // IllegalArgumentException cannot happen here
            delegate.addNetworkInterceptor(
                ApmInstrumentationOkHttpAdapter(apmInstrumentation, registry)
            )
        }

        return delegate.build()
    }

    private fun createEventListenerFactory(
        rumInstrumentation: RumNetworkInstrumentation?,
        registry: RequestTracingStateRegistry
    ): EventListener.Factory {
        val timingEventListener = rumInstrumentation?.let {
            RumInstrumentationTimingsCounter.Factory(
                rumInstrumentation,
                registry
            )
        }
        return CompositeEventListener.Factory(
            registry,
            delegates = listOfNotNull(
                timingEventListener,
                userEventListenerFactory
            )
        )
    }

    internal companion object {
        internal const val ORIGIN_RUM = "rum"
        internal const val OKHTTP_NETWORK_INSTRUMENTATION_NAME = "okhttp"
    }
}
