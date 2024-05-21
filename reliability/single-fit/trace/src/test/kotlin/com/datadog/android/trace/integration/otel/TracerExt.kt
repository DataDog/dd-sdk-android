/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.otel

import com.datadog.android.trace.integration.tests.utils.BlockingWriterWrapper
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.CoreTracer
import io.opentelemetry.api.trace.Tracer

internal fun Tracer.useBlockingWriter(): BlockingWriterWrapper {
    val coreTracer: CoreTracer = this.getFieldValue("tracer")
    val writer: Writer = coreTracer.getFieldValue("writer")
    return if (writer !is BlockingWriterWrapper) {
        val blockingWriterWrapper = BlockingWriterWrapper(writer)
        coreTracer.setFieldValue("writer", blockingWriterWrapper)
        blockingWriterWrapper
    } else {
        writer
    }
}
