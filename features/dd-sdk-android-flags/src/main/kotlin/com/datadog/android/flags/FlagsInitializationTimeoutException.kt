/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Indicates that the first evaluation context did not complete before the initialization timeout.
 *
 * The request continues and can make the client ready later.
 */
class FlagsInitializationTimeoutException(timeoutMs: Long) :
    RuntimeException("Flags initialization timed out after ${timeoutMs}ms")
