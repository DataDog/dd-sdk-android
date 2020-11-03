/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching.processors

import com.datadog.android.core.internal.data.Writer
import java.util.concurrent.ExecutorService

internal class DefaultDataProcessor<T : Any>(
    internal val executorService: ExecutorService,
    internal val writer: Writer<T>
) : DataProcessor<T> {

    override fun consume(event: T) {
        executorService.submit {
            writer.write(event)
        }
    }

    override fun consume(events: List<T>) {
        executorService.submit {
            writer.write(events)
        }
    }
}
