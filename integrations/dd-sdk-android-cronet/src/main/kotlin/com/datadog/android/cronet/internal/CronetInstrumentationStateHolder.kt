/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.trace.internal.net.RequestTraceState

internal interface CronetInstrumentationStateHolder {
    var traceState: RequestTraceState?
}
