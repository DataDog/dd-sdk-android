/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface DatadogSpanBuilder {

    fun start(): DatadogSpan

    fun withOrigin(origin: String?): DatadogSpanBuilder

    fun withTag(key: String, value: Double?): DatadogSpanBuilder

    fun withTag(key: String, value: Long?): DatadogSpanBuilder

    fun withTag(key: String, value: Any?): DatadogSpanBuilder

    fun withResourceName(resourceName: String?): DatadogSpanBuilder

    fun withParentContext(parentContext: DatadogSpanContext?): DatadogSpanBuilder

    fun withStartTimestamp(micros: Long): DatadogSpanBuilder

    fun ignoreActiveSpan(): DatadogSpanBuilder

    fun withLink(link: DatadogSpanLink): DatadogSpanBuilder
}
