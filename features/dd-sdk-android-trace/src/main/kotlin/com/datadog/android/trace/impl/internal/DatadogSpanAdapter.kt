/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.core.DDSpan

internal class DatadogSpanAdapter(internal val delegate: AgentSpan) : DatadogSpan {

    override val isRootSpan: Boolean
        get() = delegate is DDSpan && delegate.isRootSpan

    override val traceId: DatadogTraceId
        get() = DatadogTraceIdAdapter(delegate.traceId)

    override val parentSpanId: Long?
        get() = (delegate as? DDSpan)?.parentId

    override val samplingPriority: Int?
        get() = delegate.samplingPriority

    override val durationNano: Long
        get() = delegate.durationNano

    override val startTime: Long
        get() = delegate.startTime

    override val localRootSpan: DatadogSpan?
        get() = delegate.localRootSpan?.let { DatadogSpanAdapter(it) }

    override var isError: Boolean?
        get() = delegate.isError
        set(value) {
            if (value == null) return
            delegate.isError = value
        }

    override var resourceName: String?
        get() = delegate.resourceName?.toString()
        set(value) {
            delegate.resourceName = value
        }

    override var serviceName: String
        get() = delegate.serviceName
        set(value) {
            delegate.serviceName = value
        }

    override var operationName: String
        get() = delegate.operationName.toString()
        set(value) {
            delegate.operationName = value
        }

    override fun drop() = delegate.drop()

    override fun finish() = delegate.finish()

    override fun finish(finishMicros: Long) = delegate.finish(finishMicros)

    override fun context() = DatadogSpanContextAdapter(delegate.context())

    override fun setTag(tag: String?, value: String?) {
        delegate.setTag(tag, value)
    }

    override fun setTag(tag: String?, value: Boolean) {
        delegate.setTag(tag, value)
    }

    override fun setTag(tag: String?, value: Number?) {
        delegate.setTag(tag, value)
    }

    override fun setTag(tag: String?, value: Any?) {
        delegate.setTag(tag, value)
    }

    override fun getTag(tag: String?): Any? {
        return delegate.getTag(tag)
    }

    override fun setMetric(key: String, value: Int) {
        delegate.setMetric(key, value)
    }

    override fun setErrorMessage(message: String?) {
        delegate.setErrorMessage(message)
    }

    override fun addThrowable(throwable: Throwable) {
        delegate.addThrowable(throwable)
    }

    override fun addThrowable(throwable: Throwable, errorPriority: Byte) {
        delegate.addThrowable(throwable)
    }
}
