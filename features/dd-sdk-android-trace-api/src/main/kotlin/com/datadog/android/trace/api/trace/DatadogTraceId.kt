/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.trace

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Represents a Datadog trace ID, which is a unique identifier for a specific trace.
 *
 * This interface is typically used with a factory (`DatadogTraceIdFactory`) to generate or retrieve trace IDs.
 */
@NoOpImplementation
interface DatadogTraceId
