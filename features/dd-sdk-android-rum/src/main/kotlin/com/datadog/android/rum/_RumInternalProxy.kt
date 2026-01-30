/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.content.Intent
import com.datadog.android.event.EventMapper
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.RumConfiguration.Builder
import com.datadog.android.rum.configuration.RumResourceInstrumentationConfiguration
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.net.RumResourceInstrumentation
import com.datadog.android.rum.tracking.ActionTrackingStrategy
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

    fun updateExternalRefreshRate(frameTimeSeconds: Double) {
        rumMonitor.updateExternalRefreshRate(frameTimeSeconds)
    }

    fun setInternalViewAttribute(key: String, value: Any?) {
        rumMonitor.setInternalViewAttribute(key, value)
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

    /**
     * Enables the tracking of JankStats for the given activity. This should only be necessary for the
     * initial activity of an application if Datadog is initialized after that activity is created.
     * @param activity the activity to track
     */
    fun enableJankStatsTracking(activity: Activity) {
        rumMonitor.enableJankStatsTracking(activity)
    }

    fun setSyntheticsAttributeFromIntent(intent: Intent) {
        @Suppress("TooGenericExceptionCaught")
        val extras = try { intent.extras } catch (_: Exception) { null }
        val testId = extras?.getString("_dd.synthetics.test_id")
        val resultId = extras?.getString("_dd.synthetics.result_id")
        this.setSyntheticsAttribute(testId, resultId)
    }

    companion object {

        @Suppress("FunctionMaxLength")
        fun setTelemetryConfigurationEventMapper(
            builder: Builder,
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): Builder {
            return builder.setTelemetryConfigurationEventMapper(eventMapper)
        }

        @Suppress("unused")
        fun setAdditionalConfiguration(
            builder: Builder,
            additionalConfig: Map<String, Any>
        ): Builder {
            return builder.setAdditionalConfiguration(additionalConfig)
        }

        fun setComposeActionTrackingStrategy(
            builder: Builder,
            composeActionTrackingStrategy: ActionTrackingStrategy
        ): Builder {
            return builder.setComposeActionTrackingStrategy(composeActionTrackingStrategy)
        }

        fun setRumSessionTypeOverride(
            builder: Builder,
            rumSessionTypeOverride: RumSessionType
        ): Builder {
            return builder.setRumSessionTypeOverride(rumSessionTypeOverride)
        }

        fun setDisableJankStats(
            builder: Builder,
            disable: Boolean
        ): Builder {
            return builder.setDisableJankStats(disable)
        }

        fun setInsightsCollector(
            builder: Builder,
            insightsCollector: InsightsCollector
        ): Builder {
            return builder.setInsightsCollector(insightsCollector)
        }

        fun RumResourceInstrumentationConfiguration?.build(name: String): RumResourceInstrumentation? =
            this?.build(name)
    }
}
