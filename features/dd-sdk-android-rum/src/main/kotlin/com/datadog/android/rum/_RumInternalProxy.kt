/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.content.Intent
import com.datadog.android.event.EventMapper
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent

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
@InternalApi
@Suppress(
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty",
    "ClassName",
    "ClassNaming",
    "VariableNaming"
)
class _RumInternalProxy internal constructor(private val rumMonitor: AdvancedRumMonitor) {
    @Volatile private var handledSyntheticsAttribute = false

    fun addLongTask(durationNs: Long, target: String) {
        rumMonitor.addLongTask(durationNs, target)
    }

    fun updatePerformanceMetric(metric: RumPerformanceMetric, value: Double) {
        rumMonitor.updatePerformanceMetric(metric, value)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setSyntheticsAttribute(testId: String?, resultId: String?) {
        if (this.handledSyntheticsAttribute) {
            return
        }

        this.handledSyntheticsAttribute = true
        if (testId.isNullOrBlank() || resultId.isNullOrBlank()) {
            return
        }

        rumMonitor.setSyntheticsAttribute(testId, resultId)
    }

    internal fun setSyntheticsAttributeFromIntent(intent: Intent) {
        @Suppress("TooGenericExceptionCaught")
        val extras = try { intent.extras } catch (_: Exception) { null }
        val testId = extras?.getString("_dd.synthetics.test_id")
        val resultId = extras?.getString("_dd.synthetics.result_id")
        this.setSyntheticsAttribute(testId, resultId)
    }

    companion object {

        @Suppress("FunctionMaxLength")
        fun setTelemetryConfigurationEventMapper(
            builder: RumConfiguration.Builder,
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): RumConfiguration.Builder {
            return builder.setTelemetryConfigurationEventMapper(eventMapper)
        }

        fun setAdditionalConfiguration(
            builder: RumConfiguration.Builder,
            additionalConfig: Map<String, Any>
        ): RumConfiguration.Builder {
            return builder.setAdditionalConfiguration(additionalConfig)
        }
    }
}
