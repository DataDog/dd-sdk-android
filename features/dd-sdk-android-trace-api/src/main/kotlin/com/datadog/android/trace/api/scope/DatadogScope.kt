/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.scope

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A DatadogScope formalizes the activation and deactivation of a [DatadogSpan].
 */
@NoOpImplementation
interface DatadogScope {

    /**
     * Mark the end of the active period for the current context.
     */
    fun close()
}
