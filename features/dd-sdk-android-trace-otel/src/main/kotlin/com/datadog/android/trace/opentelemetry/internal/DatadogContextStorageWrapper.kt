/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry.internal

import io.opentelemetry.context.ContextStorage
import java.util.function.Function

internal class DatadogContextStorageWrapper : Function<ContextStorage, DatadogContextStorage> {
    override fun apply(wrapped: ContextStorage): DatadogContextStorage {
        return wrapped as? DatadogContextStorage ?: DatadogContextStorage(wrapped)
    }
}
