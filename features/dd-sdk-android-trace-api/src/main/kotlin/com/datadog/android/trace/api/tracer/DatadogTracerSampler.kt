/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.tracer

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Represents an interface for a custom sampling mechanism to determine whether or not
 * a span should be traced.
 */
@NoOpImplementation
interface DatadogTracerSampler
