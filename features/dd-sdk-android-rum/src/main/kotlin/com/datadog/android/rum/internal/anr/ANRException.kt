/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

internal class ANRException : Exception {

    constructor(thread: Thread) : super() {
        stackTrace = thread.stackTrace
    }

    constructor(stack: Array<StackTraceElement>) : super() {
        stackTrace = stack
    }
}
