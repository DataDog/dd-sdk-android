/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet

import com.datadog.android.cronet.internal.CronetRequestFinishedInfoListener
import com.datadog.android.cronet.internal.DatadogCronetEngine
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.configuration.RumNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.ExperimentalTraceApi
import com.datadog.android.trace.internal.DatadogTracingToolkit
import org.chromium.net.CronetEngine
import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Configures Datadog network instrumentation on this [CronetEngine.Builder].
 * Returns a [CronetIntegrationPlugin] that can be further configured before calling [CronetIntegrationPlugin.build].
 *
 * @param rumInstrumentationConfiguration optional RUM configuration. When provided, HTTP requests
 * will be automatically tracked as RUM resources with timing information.
 * @param apmInstrumentationConfiguration optional APM tracing configuration. When provided, trace spans
 * will be created for HTTP requests and tracing headers will be injected for first-party hosts.
 */
@ExperimentalTraceApi
@ExperimentalRumApi
fun CronetEngine.Builder.configureDatadogInstrumentation(
    rumInstrumentationConfiguration: RumNetworkInstrumentationConfiguration?,
    apmInstrumentationConfiguration: ApmNetworkInstrumentationConfiguration?
) = CronetIntegrationPlugin(
    this,
    rumInstrumentationConfiguration,
    apmInstrumentationConfiguration
)

/**
 * Plugin that wraps a [CronetEngine.Builder] with Datadog RUM and APM instrumentation.
 * Use [CronetEngine.Builder.configureDatadogInstrumentation] to create an instance,
 * then call [build] to produce an instrumented [CronetEngine].
 */
class CronetIntegrationPlugin internal constructor(
    private val delegate: CronetEngine.Builder,
    private val rumInstrumentationConfiguration: RumNetworkInstrumentationConfiguration?,
    private val apmInstrumentationConfiguration: ApmNetworkInstrumentationConfiguration?
) {
    private var listenerExecutor: Executor? = null

    /**
     * Sets the executor for request finished listeners.
     * If not set, a default thread pool executor will be used.
     * @param executor the executor to use for request finished listeners
     */
    @ExperimentalRumApi
    fun setListenerExecutor(executor: Executor) = apply {
        listenerExecutor = executor
    }

    /**
     * Builds the [CronetEngine] with Datadog instrumentation applied.
     * @return an instrumented [CronetEngine] instance, or a plain one if no instrumentation was configured.
     */
    fun build(): CronetEngine {
        val rumNetworkInstrumentation = rumInstrumentationConfiguration?.let {
            _RumInternalProxy.createRumNetworkInstrumentation(CRONET_NETWORK_INSTRUMENTATION_NAME, it)
        }

        val apmInstrumentation = apmInstrumentationConfiguration
            ?.takeUnless { it.isHeaderPropagationOnly() }
            ?.let { configuration ->
                DatadogTracingToolkit.createApmNetworkInstrumentation(
                    CRONET_NETWORK_INSTRUMENTATION_NAME,
                    configuration
                )
            }

        val distributedTracingInstrumentation = rumNetworkInstrumentation?.let {
            apmInstrumentationConfiguration
                ?.copy()
                ?.let { configuration ->
                    DatadogTracingToolkit.createApmNetworkInstrumentation(
                        CRONET_NETWORK_INSTRUMENTATION_NAME,
                        configuration
                            .setTraceScope(ApmNetworkTracingScope.EXCLUDE_INTERNAL_REDIRECTS)
                            .setTraceOriginIfNull(ORIGIN_RUM)
                    )
                }
        }

        val requestFinishedListener = rumNetworkInstrumentation?.let { instrumentation ->
            CronetRequestFinishedInfoListener(
                rumNetworkInstrumentation = instrumentation,
                executor = listenerExecutor ?: newListenerExecutor()
            )
        }

        return if (rumNetworkInstrumentation == null && apmInstrumentation == null) {
            delegate.build()
        } else {
            DatadogCronetEngine(
                delegate = delegate.build(),
                apmNetworkInstrumentation = apmInstrumentation,
                rumNetworkInstrumentation = rumNetworkInstrumentation,
                distributedTracingInstrumentation = distributedTracingInstrumentation
            ).also { it.addRequestFinishedListener(requestFinishedListener) }
        }
    }

    internal companion object {
        const val ORIGIN_RUM = "rum"
        const val DEFAULT_KEEP_ALIVE_TIME_SECONDS = 60L
        const val CRONET_NETWORK_INSTRUMENTATION_NAME = "cronet"

        // Exception thrown only for wrong arguments, but those ones are correct
        @Suppress("UnsafeThirdPartyFunctionCall")
        private fun newListenerExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
            0,
            Runtime.getRuntime().availableProcessors(),
            DEFAULT_KEEP_ALIVE_TIME_SECONDS,
            TimeUnit.SECONDS,
            SynchronousQueue()
        )

        private fun ApmNetworkInstrumentationConfiguration.setTraceOriginIfNull(origin: String) =
            if (getTraceOrigin() == null) {
                setTraceOrigin(origin)
            } else {
                this
            }
    }
}
