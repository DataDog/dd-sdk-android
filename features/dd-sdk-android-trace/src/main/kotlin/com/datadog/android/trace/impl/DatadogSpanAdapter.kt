/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.core.DDSpan

class DatadogSpanAdapter(internal val delegate: AgentSpan) : DatadogSpan {

    override val isRootSpan: Boolean
        get() = delegate is DDSpan && delegate.isRootSpan

    override val samplingPriority: Int?
        get() = delegate.samplingPriority

    override var isError: Boolean?
        get() = delegate.isError
        set(value) {
            if (value == null) return
            delegate.isError = value
        }

    override var resourceName: CharSequence?
        get() = delegate.resourceName
        set(value) {
            delegate.resourceName = value
        }

    override fun drop() = delegate.drop()
    override fun finish() = delegate.finish()
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
}
