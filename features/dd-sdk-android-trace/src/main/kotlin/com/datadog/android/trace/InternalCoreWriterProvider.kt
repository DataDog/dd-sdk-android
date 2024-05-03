/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

/**
 * Internal interface to provide the core writer to the tracer.
 */
interface InternalCoreWriterProvider {

    /**
     * Returns the core writer used by the tracer.
     */
    fun getCoreTracerWriter(): com.datadog.trace.common.writer.Writer
}
