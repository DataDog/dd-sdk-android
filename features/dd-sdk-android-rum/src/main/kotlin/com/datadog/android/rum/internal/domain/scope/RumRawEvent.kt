/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.model.ActionEvent

internal sealed class RumRawEvent {

    abstract val eventTime: Time

    internal data class StartView(
        val key: RumScopeKey,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopView(
        val key: RumScopeKey,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StartAction(
        val type: RumActionType,
        val name: String,
        val waitForStop: Boolean,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopAction(
        val type: RumActionType?,
        val name: String?,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StartResource(
        val key: Any,
        val url: String,
        val method: RumResourceMethod,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class WaitForResourceTiming(
        val key: Any,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddResourceTiming(
        val key: Any,
        val timing: ResourceTiming,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopResource(
        val key: Any,
        val statusCode: Long?,
        val size: Long?,
        val kind: RumResourceKind,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopResourceWithError(
        val key: Any,
        val statusCode: Long?,
        val message: String,
        val source: RumErrorSource,
        val throwable: Throwable,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopResourceWithStackTrace(
        val key: Any,
        val statusCode: Long?,
        val message: String,
        val source: RumErrorSource,
        val stackTrace: String,
        val errorType: String?,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddError(
        val message: String,
        val source: RumErrorSource,
        val throwable: Throwable?,
        val stacktrace: String?,
        val isFatal: Boolean,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time(),
        val type: String? = null,
        val sourceType: RumErrorSourceType = RumErrorSourceType.ANDROID,
        val threads: List<ThreadDump>,
        val timeSinceAppStartNs: Long? = null
    ) : RumRawEvent()

    internal data class ResourceSent(
        val viewId: String,
        val resourceId: String,
        val resourceEndTimestampInNanos: Long,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ActionSent(
        val viewId: String,
        val frustrationCount: Int,
        val type: ActionEvent.ActionEventActionType,
        val eventEndTimestampInNanos: Long,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ErrorSent(
        val viewId: String,
        val resourceId: String? = null,
        val resourceEndTimestampInNanos: Long? = null,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class LongTaskSent(
        val viewId: String,
        val isFrozenFrame: Boolean = false,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ResourceDropped(
        val viewId: String,
        val resourceId: String,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ActionDropped(
        val viewId: String,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ErrorDropped(
        val viewId: String,
        val resourceId: String? = null,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class LongTaskDropped(
        val viewId: String,
        val isFrozenFrame: Boolean = false,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ResetSession(
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class KeepAlive(
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class ApplicationStarted(
        override val eventTime: Time,
        val applicationStartupNanos: Long
    ) : RumRawEvent()

    internal data class AddCustomTiming(
        val name: String,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddViewLoadingTime(
        val overwrite: Boolean,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddViewAttributes(
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class RemoveViewAttributes(
        val attributes: Collection<String>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddLongTask(
        val durationNs: Long,
        val target: String,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class SendCustomActionNow(
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddFeatureFlagEvaluation(
        val name: String,
        val value: Any,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class AddFeatureFlagEvaluations(
        val featureFlags: Map<String, Any>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopSession(
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class UpdatePerformanceMetric(
        val metric: RumPerformanceMetric,
        val value: Double,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class UpdateExternalRefreshRate(
        val frameTimeSeconds: Double,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class SetInternalViewAttribute(
        val key: String,
        val value: Any?,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class SetSyntheticsTestAttribute(
        val testId: String,
        val resultId: String,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class WebViewEvent(override val eventTime: Time = Time()) : RumRawEvent()

    internal data class TelemetryEventWrapper(
        val event: InternalTelemetryEvent,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class SdkInit(
        val isAppInForeground: Boolean,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StartFeatureOperation(
        val name: String,
        val operationKey: String?,
        val attributes: Map<String, Any?>,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal data class StopFeatureOperation(
        val name: String,
        val operationKey: String?,
        val attributes: Map<String, Any?>,
        val failureReason: FailureReason? = null,
        override val eventTime: Time = Time()
    ) : RumRawEvent()

    internal class AppStartTTIDEvent(
        override val eventTime: Time = Time(),
        val info: RumTTIDInfo
    ) : RumRawEvent()
}
