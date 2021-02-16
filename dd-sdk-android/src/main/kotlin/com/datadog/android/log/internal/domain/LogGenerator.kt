/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import io.opentracing.util.GlobalTracer

internal class LogGenerator(
    internal val serviceName: String,
    internal val loggerName: String,
    internal val networkInfoProvider: NetworkInfoProvider?,
    internal val userInfoProvider: UserInfoProvider,
    envName: String,
    appVersion: String
) {

    private val envTag: String? = if (envName.isNotEmpty()) {
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
    fun generateLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String? = null,
        bundleWithTraces: Boolean = true,
        bundleWithRum: Boolean = true,
        userInfo: UserInfo? = null,
        networkInfo: NetworkInfo? = null
    ): Log {
        val combinedAttributes = resolveAttributes(attributes, bundleWithTraces, bundleWithRum)
        val combinedTags = resolveTags(tags)
        return Log(
            serviceName = serviceName,
            level = level,
            message = message,
            timestamp = timestamp,
            throwable = throwable,
            attributes = combinedAttributes,
            tags = combinedTags.toList(),
            networkInfo = networkInfo ?: networkInfoProvider?.getLatestNetworkInfo(),
            userInfo = userInfo ?: userInfoProvider.getUserInfo(),
            loggerName = loggerName,
            threadName = threadName ?: Thread.currentThread().name
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
        }
        return combinedAttributes
    }
}
