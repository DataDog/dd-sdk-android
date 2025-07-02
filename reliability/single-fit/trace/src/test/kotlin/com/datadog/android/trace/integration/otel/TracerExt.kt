/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.otel

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.trace.integration.tests.utils.BlockingWriterWrapper
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import com.datadog.trace.common.writer.Writer

private const val WRITER_FIELD_NAME = "coreTracerDataWriter"

internal fun FeatureScope?.useBlockingWriter(): BlockingWriterWrapper {
    val feature = this?.unwrap<Feature>() ?: throw NullPointerException("Can't cast to Feature")
    val writer: Writer = feature.getFieldValue(WRITER_FIELD_NAME)
    return if (writer is BlockingWriterWrapper) {
        writer
    } else {
        val blockingWriterWrapper = BlockingWriterWrapper(writer)
        feature.setFieldValue(WRITER_FIELD_NAME, blockingWriterWrapper)
        blockingWriterWrapper
    }
}
