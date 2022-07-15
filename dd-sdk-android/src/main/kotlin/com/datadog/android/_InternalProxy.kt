/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.telemetry.internal.Telemetry

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
    "ClassNaming",
    "VariableNaming"
)
class _InternalProxy {
    class _TelemetryProxy {
        private val telemetry: Telemetry

        internal constructor(telemetry: Telemetry) {
            this.telemetry = telemetry
        }

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

    val _telemetry: _TelemetryProxy

    internal constructor(telemetry: Telemetry) {
        _telemetry = _TelemetryProxy(telemetry)
    }
}
