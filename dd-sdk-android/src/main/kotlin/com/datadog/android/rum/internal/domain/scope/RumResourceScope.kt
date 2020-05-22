/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventData

internal class RumResourceScope(
    val parentScope: RumScope,
    val url: String,
    val method: String,
    val key: String,
    initialAttributes: Map<String, Any?>
) : RumScope {

    val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()
    var timing: RumEventData.Resource.Timing? = null

    internal val eventTimestamp = RumFeature.timeProvider.getDeviceTimestamp()
    internal val startedNanos: Long = System.nanoTime()
    private val networkInfo = RumFeature.networkInfoProvider.getLatestNetworkInfo()

    private var sent = false

    // region RumScope

    override fun handleEvent(event: RumRawEvent, writer: Writer<RumEvent>): RumScope? {
        if (key == null) {
            onStopResource(null, writer)
        } else if (event is RumRawEvent.AddResourceTiming) {
            if (key == event.key) timing = event.timing
        } else if (event is RumRawEvent.StopResource) {
            if (key == event.key) onStopResource(event, writer)
        } else if (event is RumRawEvent.StopResourceWithError) {
            if (key == event.key) {
                onStopResourceWithError(event, writer)
            }
        }
        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    // endregion

    // region  Internal

    private fun onStopResource(
        event: RumRawEvent.StopResource?,
        writer: Writer<RumEvent>
    ) {
        if (event != null) attributes.putAll(event.attributes)
        sendResource(
            event?.kind ?: RumResourceKind.UNKNOWN,
            writer
        )
    }

    private fun onStopResourceWithError(
        event: RumRawEvent.StopResourceWithError,
        writer: Writer<RumEvent>
    ) {
        attributes[RumAttributes.HTTP_URL] = url
        sendError(
            event.message,
            event.origin,
            event.throwable,
            writer
        )
    }

    private fun sendResource(
        kind: RumResourceKind,
        writer: Writer<RumEvent>
    ) {
        attributes.putAll(GlobalRum.globalAttributes)
        val eventData = RumEventData.Resource(
            kind,
            method,
            url,
            System.nanoTime() - startedNanos,
            timing
        )
        val event = RumEvent(
            getRumContext(),
            eventTimestamp,
            eventData,
            RumFeature.userInfoProvider.getUserInfo(),
            attributes,
            networkInfo
        )
        writer.write(event)
        parentScope.handleEvent(RumRawEvent.SentResource(), writer)
        sent = true
    }

    private fun sendError(
        message: String,
        origin: String,
        throwable: Throwable,
        writer: Writer<RumEvent>
    ) {
        attributes.putAll(GlobalRum.globalAttributes)
        val eventData = RumEventData.Error(
            message,
            origin,
            throwable
        )
        val event = RumEvent(
            getRumContext(),
            eventTimestamp,
            eventData,
            RumFeature.userInfoProvider.getUserInfo(),
            attributes,
            networkInfo
        )
        writer.write(event)
        parentScope.handleEvent(RumRawEvent.SentError(), writer)
        sent = true
    }

    // endregion

    companion object {

        fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartResource
        ): RumScope {
            return RumResourceScope(
                parentScope,
                event.url,
                event.method,
                event.key,
                event.attributes
            )
        }
    }
}
