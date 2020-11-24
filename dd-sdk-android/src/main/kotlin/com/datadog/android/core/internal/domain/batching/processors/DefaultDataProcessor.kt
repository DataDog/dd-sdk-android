/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching.processors

import com.datadog.android.core.internal.data.Writer
import java.util.concurrent.ExecutorService

internal class DefaultDataProcessor<T : Any>(
    val executorService: ExecutorService,
    val dataWriter: Writer<T>
) : DataProcessor<T> {

    override fun consume(event: T) {
        executorService.submit {
            dataWriter.write(event)
        }
    }

    override fun consume(events: List<T>) {
        executorService.submit {
            dataWriter.write(events)
        }
    }

    override fun getWriter(): Writer<T> {
        return dataWriter
    }
}
