/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

import com.datadog.android.internal.utils.loggableStackTrace

@Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "UndocumentedPublicProperty")
sealed class InternalTelemetryEvent {

    sealed class Log(val message: String, val additionalProperties: Map<String, Any?>?) : InternalTelemetryEvent() {
        class Debug(message: String, additionalProperties: Map<String, Any?>?) : Log(message, additionalProperties)

        class Error(
            message: String,
            additionalProperties: Map<String, Any?>? = null,
            val error: Throwable? = null,
            val stacktrace: String? = null,
            val kind: String? = null
        ) : Log(message, additionalProperties) {
            fun resolveKind(): String? {
                return kind ?: error?.javaClass?.canonicalName ?: error?.javaClass?.simpleName
            }

            fun resolveStacktrace(): String? {
                return stacktrace ?: error?.loggableStackTrace()
            }
        }
    }

    data class Configuration(
        val trackErrors: Boolean,
        val batchSize: Long,
        val batchUploadFrequency: Long,
        val useProxy: Boolean,
        val useLocalEncryption: Boolean,
        val batchProcessingLevel: Int
    ) : InternalTelemetryEvent()

    data class Metric(
        val message: String,
        val additionalProperties: Map<String, Any?>?
    ) : InternalTelemetryEvent()

    sealed class ApiUsage(val additionalProperties: MutableMap<String, Any?> = mutableMapOf()) :
        InternalTelemetryEvent() {
        class AddViewLoadingTime(
            val overwrite: Boolean,
            val noView: Boolean,
            val noActiveView: Boolean,
            additionalProperties: MutableMap<String, Any?> = mutableMapOf()
        ) : ApiUsage(additionalProperties)
    }

    object InterceptorInstantiated : InternalTelemetryEvent()
}
