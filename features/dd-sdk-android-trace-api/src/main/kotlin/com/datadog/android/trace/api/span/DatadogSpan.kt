/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

interface DatadogSpan {
    var isError: Boolean?
    val isRootSpan: Boolean
    val samplingPriority: Int?
    var resourceName: CharSequence?

    fun context(): DatadogSpanContext

    fun finish()
    fun drop()

    fun setTag(tag: String?, value: String?)
    fun setTag(tag: String?, value: Boolean)
    fun setTag(tag: String?, value: Number?)
}
