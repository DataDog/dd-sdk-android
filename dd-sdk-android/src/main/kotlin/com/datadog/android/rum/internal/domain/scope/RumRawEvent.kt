/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.RumResourceKind

internal sealed class RumRawEvent {

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
        val name: String,
        val waitForStop: Boolean,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StopAction(
        val name: String,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StartResource(
        val key: Any,
        val url: String,
        val method: String,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StopResource(
        val key: Any,
        val kind: RumResourceKind,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal data class StopResourceWithError(
        val key: Any,
        val message: String,
        val origin: String,
        val throwable: Throwable
    ) : RumRawEvent()

    internal data class AddError(
        val message: String,
        val origin: String,
        val throwable: Throwable?,
        val attributes: Map<String, Any?>
    ) : RumRawEvent()

    internal class SentResource : RumRawEvent()

    internal class SentAction : RumRawEvent()

    internal class SentError : RumRawEvent()

    internal class ViewTreeChanged : RumRawEvent()
}
