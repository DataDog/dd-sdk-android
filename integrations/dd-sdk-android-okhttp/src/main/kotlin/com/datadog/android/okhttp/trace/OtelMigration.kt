/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer

// This code aimed to reduce amount of lines in PR for review. Gonna replace with actual class just before merging v3
typealias Tracer = AgentTracer.TracerAPI
typealias Span = AgentSpan
typealias SpanContext = AgentSpan.Context
