/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.domain.Time
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.model.ViewEvent

internal sealed class RumRawEvent {

    open val eventTime = Time()

    internal data class StartView(
        val key: Any,
        val name: String,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StopView(
        val key: Any,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StartAction(
        val type: RumActionType,
        val name: String,
        val waitForStop: Boolean,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StopAction(
        val type: RumActionType,
        val name: String,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StartResource(
        val key: String,
        val url: String,
        val method: String,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class WaitForResourceTiming(
        val key: String
    ) : RumRawEvent()

    internal data class AddResourceTiming(
        val key: String,
        val timing: ResourceTiming
    ) : RumRawEvent()

    internal data class StopResource(
        val key: String,
        val statusCode: Long?,
        val size: Long?,
        val kind: RumResourceKind,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StopResourceWithError(
        val key: String,
        val statusCode: Long?,
        val message: String,
        val source: RumErrorSource,
        val throwable: Throwable
    ) : RumRawEvent()

    internal data class AddError(
        val message: String,
        val source: RumErrorSource,
        val throwable: Throwable?,
        val isFatal: Boolean,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class UpdateViewLoadingTime(
        val key: Any,
        val loadingTime: Long,
        val loadingType: ViewEvent.LoadingType
    ) : RumRawEvent()

    internal class SentResource : RumRawEvent()

    internal class SentAction : RumRawEvent()

    internal class SentError : RumRawEvent()

    internal class ViewTreeChanged : RumRawEvent()

    internal class ResetSession : RumRawEvent()

    internal class KeepAlive : RumRawEvent()

    internal data class ApplicationStarted(
        override val eventTime: Time
    ) : RumRawEvent()
}
