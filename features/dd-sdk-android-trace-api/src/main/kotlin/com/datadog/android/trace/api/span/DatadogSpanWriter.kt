/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

/**
 * A writer is responsible to send collected spans to some place.
 * This wrapper is required to no expose CoreTracer wrapper to dependant modules
 * */
interface DatadogSpanWriter
