/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
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
        private val telemetry: Telemetry = Telemetry()

        fun debug(message: String) {
            telemetry.debug(message)
        }

        fun error(message: String, stack: String?, kind: String?) {
            telemetry.error(message, stack, kind)
        }
    }

    class _RumProxy {
        private val rumMonitor: AdvancedRumMonitor

        internal constructor(rumMonitor: AdvancedRumMonitor) {
            this.rumMonitor = rumMonitor
        }

        fun addLongTask(durationNs: Long, target: String) {
            rumMonitor.addLongTask(durationNs, target)
        }
    }

    val _telemetry: _TelemetryProxy = _TelemetryProxy()
    val _rumProxy: _RumProxy?

    internal constructor(rumMonitor: AdvancedRumMonitor?) {
        _rumProxy = rumMonitor?.let { _RumProxy(rumMonitor) }
    }
}
