/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.v2.core.internal.ContextProvider
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
import java.util.UUID

internal class RumResourceScope(
    internal val parentScope: RumScope,
    internal val url: String,
    internal val method: String,
    internal val key: String,
    eventTime: Time,
    initialAttributes: Map<String, Any?>,
    serverTimeOffsetInMs: Long,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    private val rumEventSourceProvider: RumEventSourceProvider,
    private val contextProvider: ContextProvider,
    private val featuresContextResolver: FeaturesContextResolver
) : RumScope {

    internal val resourceId: String = UUID.randomUUID().toString()
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap().apply {
        putAll(GlobalRum.globalAttributes)
    }
    private var timing: ResourceTiming? = null
    private val initialContext = parentScope.getRumContext()

    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs
    private val startedNanos: Long = eventTime.nanoTime
    private val networkInfo = contextProvider.context.networkInfo

    private var sent = false
    private var waitForTiming = false
    internal var stopped = false
    private var kind: RumResourceKind = RumResourceKind.UNKNOWN
    private var statusCode: Long? = null
    private var size: Long? = null

    // region RumScope

    @WorkerThread
    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope? {
        when (event) {
            is RumRawEvent.WaitForResourceTiming -> if (key == event.key) waitForTiming = true
            is RumRawEvent.AddResourceTiming -> onAddResourceTiming(event, writer)
            is RumRawEvent.StopResource -> onStopResource(event, writer)
            is RumRawEvent.StopResourceWithError -> onStopResourceWithError(event, writer)
            is RumRawEvent.StopResourceWithStackTrace -> onStopResourceWithStackTrace(event, writer)
            else -> {
                // Other events are not relevant for RumResourceScope
            }
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return initialContext
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region  Internal

    @WorkerThread
    private fun onStopResource(
        event: RumRawEvent.StopResource,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return

        stopped = true
        attributes.putAll(event.attributes)
        kind = event.kind
        statusCode = event.statusCode
        size = event.size

        if (!(waitForTiming && timing == null)) {
            sendResource(kind, event.statusCode, event.size, event.eventTime, writer)
        }
    }

    @WorkerThread
    private fun onAddResourceTiming(
        event: RumRawEvent.AddResourceTiming,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return

        timing = event.timing
        if (stopped && !sent) {
            sendResource(kind, statusCode, size, event.eventTime, writer)
        }
    }

    @WorkerThread
    private fun onStopResourceWithError(
        event: RumRawEvent.StopResourceWithError,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return

        attributes.putAll(event.attributes)

        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.throwable.loggableStackTrace(),
            event.throwable.javaClass.canonicalName,
            writer
        )
    }

    @WorkerThread
    private fun onStopResourceWithStackTrace(
        event: RumRawEvent.StopResourceWithStackTrace,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return

        attributes.putAll(event.attributes)

        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.stackTrace,
            event.errorType,
            writer
        )
    }

    @Suppress("LongMethod")
    @WorkerThread
    private fun sendResource(
        kind: RumResourceKind,
        statusCode: Long?,
        size: Long?,
        eventTime: Time,
        writer: DataWriter<Any>
    ) {
        attributes.putAll(GlobalRum.globalAttributes)
        val traceId = attributes.remove(RumAttributes.TRACE_ID)?.toString()
        val spanId = attributes.remove(RumAttributes.SPAN_ID)?.toString()

        val sdkContext = contextProvider.context
        val rumContext = getRumContext()
        val user = sdkContext.userInfo
        val hasReplay = featuresContextResolver.resolveHasReplay(sdkContext)

        @Suppress("UNCHECKED_CAST")
        val finalTiming = timing ?: extractResourceTiming(
            attributes.remove(RumAttributes.RESOURCE_TIMINGS) as? Map<String, Any?>
        )
        val duration = resolveResourceDuration(eventTime)
        val resourceEvent = ResourceEvent(
            date = eventTimestamp,
            resource = ResourceEvent.Resource(
                id = resourceId,
                type = kind.toSchemaType(),
                url = url,
                duration = duration,
                method = method.toMethod(),
                statusCode = statusCode,
                size = size,
                dns = finalTiming?.dns(),
                connect = finalTiming?.connect(),
                ssl = finalTiming?.ssl(),
                firstByte = finalTiming?.firstByte(),
                download = finalTiming?.download(),
                provider = resolveResourceProvider()
            ),
            action = rumContext.actionId?.let { ResourceEvent.Action(it) },
            view = ResourceEvent.View(
                id = rumContext.viewId.orEmpty(),
                name = rumContext.viewName,
                url = rumContext.viewUrl.orEmpty()
            ),
            usr = ResourceEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email,
                additionalProperties = user.additionalProperties
            ),
            connectivity = networkInfo.toResourceConnectivity(),
            application = ResourceEvent.Application(rumContext.applicationId),
            session = ResourceEvent.ResourceEventSession(
                id = rumContext.sessionId,
                type = ResourceEvent.ResourceEventSessionType.USER,
                hasReplay = hasReplay
            ),
            source = rumEventSourceProvider.resourceEventSource,
            os = ResourceEvent.Os(
                name = sdkContext.deviceInfo.osName,
                version = sdkContext.deviceInfo.osVersion,
                versionMajor = sdkContext.deviceInfo.osMajorVersion
            ),
            device = ResourceEvent.Device(
                type = sdkContext.deviceInfo.deviceType.toResourceSchemaType(),
                name = sdkContext.deviceInfo.deviceName,
                model = sdkContext.deviceInfo.deviceModel,
                brand = sdkContext.deviceInfo.deviceBrand,
                architecture = sdkContext.deviceInfo.architecture
            ),
            context = ResourceEvent.Context(additionalProperties = attributes),
            dd = ResourceEvent.Dd(
                traceId = traceId,
                spanId = spanId,
                session = ResourceEvent.DdSession(plan = ResourceEvent.Plan.PLAN_1)
            )
        )
        writer.write(resourceEvent)
        sent = true
    }

    private fun resolveResourceDuration(eventTime: Time): Long {
        val duration = eventTime.nanoTime - startedNanos
        return if (duration <= 0) {
            devLogger.w(NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, url))
            1
        } else {
            duration
        }
    }

    private fun resolveResourceProvider(): ResourceEvent.Provider? {
        return if (firstPartyHostDetector.isFirstPartyUrl(url)) {
            ResourceEvent.Provider(
                resolveDomain(url),
                type = ResourceEvent.ProviderType.FIRST_PARTY
            )
        } else {
            null
        }
    }

    @SuppressWarnings("LongParameterList", "LongMethod")
    @WorkerThread
    private fun sendError(
        message: String,
        source: RumErrorSource,
        statusCode: Long?,
        stackTrace: String?,
        errorType: String?,
        writer: DataWriter<Any>
    ) {
        attributes.putAll(GlobalRum.globalAttributes)

        val rumContext = getRumContext()
        val sdkContext = contextProvider.context
        val user = sdkContext.userInfo
        val hasReplay = featuresContextResolver.resolveHasReplay(sdkContext)

        val errorEvent = ErrorEvent(
            date = eventTimestamp,
            error = ErrorEvent.Error(
                message = message,
                source = source.toSchemaSource(),
                stack = stackTrace,
                isCrash = false,
                resource = ErrorEvent.Resource(
                    url = url,
                    method = method.toErrorMethod(),
                    statusCode = statusCode ?: 0,
                    provider = resolveErrorProvider()
                ),
                type = errorType,
                sourceType = ErrorEvent.SourceType.ANDROID
            ),
            action = rumContext.actionId?.let { ErrorEvent.Action(it) },
            view = ErrorEvent.View(
                id = rumContext.viewId.orEmpty(),
                name = rumContext.viewName,
                url = rumContext.viewUrl.orEmpty()
            ),
            usr = ErrorEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email,
                additionalProperties = user.additionalProperties
            ),
            connectivity = networkInfo.toErrorConnectivity(),
            application = ErrorEvent.Application(rumContext.applicationId),
            session = ErrorEvent.ErrorEventSession(
                id = rumContext.sessionId,
                type = ErrorEvent.ErrorEventSessionType.USER,
                hasReplay = hasReplay
            ),
            source = rumEventSourceProvider.errorEventSource,
            os = ErrorEvent.Os(
                name = sdkContext.deviceInfo.osName,
                version = sdkContext.deviceInfo.osVersion,
                versionMajor = sdkContext.deviceInfo.osMajorVersion
            ),
            device = ErrorEvent.Device(
                type = sdkContext.deviceInfo.deviceType.toErrorSchemaType(),
                name = sdkContext.deviceInfo.deviceName,
                model = sdkContext.deviceInfo.deviceModel,
                brand = sdkContext.deviceInfo.deviceBrand,
                architecture = sdkContext.deviceInfo.architecture
            ),
            context = ErrorEvent.Context(additionalProperties = attributes),
            dd = ErrorEvent.Dd(session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1))
        )
        writer.write(errorEvent)
        sent = true
    }

    private fun resolveErrorProvider(): ErrorEvent.Provider? {
        return if (firstPartyHostDetector.isFirstPartyUrl(url)) {
            ErrorEvent.Provider(
                domain = resolveDomain(url),
                type = ErrorEvent.ProviderType.FIRST_PARTY
            )
        } else {
            null
        }
    }

    private fun resolveDomain(url: String): String {
        return try {
            URL(url).host
        } catch (e: MalformedURLException) {
            url
        }
    }

    // endregion

    companion object {

        internal const val NEGATIVE_DURATION_WARNING_MESSAGE = "The computed duration for your " +
            "resource: %s was 0 or negative. In order to keep the resource event" +
            " we forced it to 1ns."

        @Suppress("LongParameterList")
        fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartResource,
            firstPartyHostDetector: FirstPartyHostDetector,
            timestampOffset: Long,
            rumEventSourceProvider: RumEventSourceProvider,
            contextProvider: ContextProvider,
            featuresContextResolver: FeaturesContextResolver
        ): RumScope {
            return RumResourceScope(
                parentScope,
                event.url,
                event.method,
                event.key,
                event.eventTime,
                event.attributes,
                timestampOffset,
                firstPartyHostDetector,
                rumEventSourceProvider,
                contextProvider,
                featuresContextResolver
            )
        }
    }
}
