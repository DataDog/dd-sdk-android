/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.event.EventMapper
import com.datadog.android.telemetry.internal.Telemetry
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.v2.core.DatadogCore
import com.datadog.android.webview.internal.MixedWebViewEventConsumer
import com.datadog.android.webview.internal.NoOpWebViewEventConsumer
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.log.WebViewLogEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class  are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@Suppress(
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty",
    "ClassName",
    "ClassNaming",
    "VariableNaming"
)
class _InternalProxy internal constructor(
    telemetry: Telemetry,
    // TODO RUMM-0000 Shouldn't be nullable
    private val datadogCore: DatadogCore?
) {
    class _TelemetryProxy internal constructor(private val telemetry: Telemetry) {

        fun debug(message: String) {
            telemetry.debug(message)
        }

        fun error(message: String, throwable: Throwable? = null) {
            telemetry.error(message, throwable)
        }

        fun error(message: String, stack: String?, kind: String?) {
            telemetry.error(message, stack, kind)
        }
    }

    class _WebviewProxy internal constructor(private val datadogCore: DatadogCore?) {
        private fun buildWebViewEventConsumer(): WebViewEventConsumer<String> {
            val core = datadogCore
            val webViewRumFeature = core?.webViewRumFeature
            val webViewLogsFeature = core?.webViewLogsFeature
            val contextProvider = WebViewRumEventContextProvider()

            if (webViewLogsFeature == null || webViewRumFeature == null) {
                return NoOpWebViewEventConsumer()
            } else {
                return MixedWebViewEventConsumer(
                    WebViewRumEventConsumer(
                        sdkCore = core,
                        dataWriter = webViewRumFeature.dataWriter,
                        contextProvider = contextProvider
                    ),
                    WebViewLogEventConsumer(
                        sdkCore = core,
                        userLogsWriter = webViewLogsFeature.dataWriter,
                        rumContextProvider = contextProvider
                    )
                )
            }
        }

        internal val webViewEventConsumer: WebViewEventConsumer<String> = buildWebViewEventConsumer()
    }

    private val _webview: _WebviewProxy = _WebviewProxy(datadogCore)
    val _telemetry: _TelemetryProxy = _TelemetryProxy(telemetry)

    fun setCustomAppVersion(version: String) {
        datadogCore?.coreFeature?.packageVersionProvider?.version = version
    }

    fun consumeWebviewEvent(event: String) {
        @Suppress("ThreadSafety") // Assumed to be on a background thread
        _webview.webViewEventConsumer.consume(event)
    }

    companion object {
        @Suppress("FunctionMaxLength")
        fun setTelemetryConfigurationEventMapper(
            builder: Configuration.Builder,
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): Configuration.Builder {
            return builder.setTelemetryConfigurationEventMapper(eventMapper)
        }
    }
}
