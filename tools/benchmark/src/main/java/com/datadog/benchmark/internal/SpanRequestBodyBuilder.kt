/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal

import com.datadog.benchmark.internal.model.BenchmarkContext
import com.datadog.benchmark.internal.model.SpanEvent

internal class SpanRequestBodyBuilder(private val context: BenchmarkContext) : RequestBodyBuilder<SpanEvent> {

    private val serializer = SpanEventSerializer()

    override fun build(data: List<SpanEvent>): String {
        return serializer.serialize(
            context.env,
            data
        )
    }
}
