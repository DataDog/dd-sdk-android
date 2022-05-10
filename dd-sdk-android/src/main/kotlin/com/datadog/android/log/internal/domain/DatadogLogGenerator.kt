/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.log.internal.utils.buildLogDateFormat
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.GlobalRum
import io.opentracing.util.GlobalTracer
import java.util.Date

internal class DatadogLogGenerator(
    internal val serviceName: String,
    internal val loggerName: String,
    internal val networkInfoProvider: NetworkInfoProvider?,
    internal val userInfoProvider: UserInfoProvider,
    internal val timeProvider: TimeProvider,
    internal val sdkVersion: String,
    envName: String,
    appVersion: String
) : LogGenerator {

    private val simpleDateFormat = buildLogDateFormat()

    internal val envTag: String? = if (envName.isNotEmpty()) {
        "${LogAttributes.ENV}:$envName"
    } else {
        null
    }

    private val appVersionTag = if (appVersion.isNotEmpty()) {
        "${LogAttributes.APPLICATION_VERSION}:$appVersion"
    } else {
        null
    }

    @Suppress("LongParameterList")
    override fun generateLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String?,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        networkInfo: NetworkInfo?
    ): LogEvent {
        val resolvedTimestamp = timestamp + timeProvider.getServerOffsetMillis()
        val combinedAttributes = resolveAttributes(attributes, bundleWithTraces, bundleWithRum)
        val formattedDate = synchronized(simpleDateFormat) {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            simpleDateFormat.format(Date(resolvedTimestamp))
        }
        val combinedTags = resolveTags(tags)
        val error = throwable?.let {
            val kind = it.javaClass.canonicalName ?: it.javaClass.simpleName
            LogEvent.Error(kind = kind, stack = it.stackTraceToString(), message = it.message)
        }
        val usr = resolveUserInfo(userInfo)
        val network = resolveNetworkInfo(networkInfo)
        val loggerInfo = LogEvent.Logger(
            name = loggerName,
            threadName = threadName ?: Thread.currentThread().name,
            version = sdkVersion
        )
        return LogEvent(
            service = serviceName,
            status = resolveLogLevelStatus(level),
            message = message,
            date = formattedDate,
            error = error,
            logger = loggerInfo,
            usr = usr,
            network = network,
            ddtags = combinedTags.joinToString(separator = ","),
            additionalProperties = combinedAttributes
        )
    }

    private fun resolveNetworkInfo(networkInfo: NetworkInfo?): LogEvent.Network? {
        val resolvedNetworkInfo = networkInfo ?: networkInfoProvider?.getLatestNetworkInfo()
        return resolvedNetworkInfo?.let {
            LogEvent.Network(
                LogEvent.Client(
                    simCarrier = resolveSimCarrier(it),
                    signalStrength = it.strength?.toString(),
                    downlinkKbps = it.downKbps?.toString(),
                    uplinkKbps = it.upKbps?.toString(),
                    connectivity = it.connectivity.toString()
                )
            )
        }
    }

    private fun resolveUserInfo(userInfo: UserInfo?): LogEvent.Usr {
        val resolvedUserInfo = userInfo ?: userInfoProvider.getUserInfo()
        return LogEvent.Usr(
            name = resolvedUserInfo.name,
            email = resolvedUserInfo.email,
            id = resolvedUserInfo.id,
            additionalProperties = resolvedUserInfo.additionalProperties
        )
    }

    private fun resolveTags(
        tags: Set<String>
    ): MutableSet<String> {
        val combinedTags = mutableSetOf<String>().apply { addAll(tags) }
        envTag?.let {
            combinedTags.add(it)
        }
        appVersionTag?.let {
            combinedTags.add(it)
        }

        return combinedTags
    }

    private fun resolveAttributes(
        attributes: Map<String, Any?>,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean
    ): MutableMap<String, Any?> {
        val combinedAttributes = mutableMapOf<String, Any?>().apply { putAll(attributes) }
        if (bundleWithTraces && GlobalTracer.isRegistered()) {
            val tracer = GlobalTracer.get()
            val activeContext = tracer.activeSpan()?.context()
            if (activeContext != null) {
                combinedAttributes[LogAttributes.DD_TRACE_ID] = activeContext.toTraceId()
                combinedAttributes[LogAttributes.DD_SPAN_ID] = activeContext.toSpanId()
            }
        }
        if (bundleWithRum && GlobalRum.isRegistered()) {
            val activeContext = GlobalRum.getRumContext()
            combinedAttributes[LogAttributes.RUM_APPLICATION_ID] = activeContext.applicationId
            combinedAttributes[LogAttributes.RUM_SESSION_ID] = activeContext.sessionId
            combinedAttributes[LogAttributes.RUM_VIEW_ID] = activeContext.viewId
            combinedAttributes[LogAttributes.RUM_ACTION_ID] = activeContext.actionId
        }
        return combinedAttributes
    }

    @Suppress("DEPRECATION")
    private fun resolveLogLevelStatus(level: Int): LogEvent.Status {
        return when (level) {
            android.util.Log.ASSERT -> LogEvent.Status.CRITICAL
            android.util.Log.ERROR -> LogEvent.Status.ERROR
            android.util.Log.WARN -> LogEvent.Status.WARN
            android.util.Log.INFO -> LogEvent.Status.INFO
            android.util.Log.DEBUG -> LogEvent.Status.DEBUG
            android.util.Log.VERBOSE -> LogEvent.Status.TRACE
            DatadogLogGenerator.CRASH -> LogEvent.Status.EMERGENCY
            else -> LogEvent.Status.DEBUG
        }
    }

    private fun resolveSimCarrier(networkInfo: NetworkInfo): LogEvent.SimCarrier? {
        return if (networkInfo.carrierId != null || networkInfo.carrierName != null) {
            LogEvent.SimCarrier(
                id = networkInfo.carrierId?.toString(),
                name = networkInfo.carrierName
            )
        } else {
            null
        }
    }

    companion object {
        internal const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        internal const val CRASH: Int = 9
    }
}
