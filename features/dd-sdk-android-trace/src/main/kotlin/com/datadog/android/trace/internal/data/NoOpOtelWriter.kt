/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.data

import com.datadog.opentracing.DDSpan
import com.datadog.trace.common.writer.Writer

internal class NoOpOtelWriter : datadog.trace.common.writer.Writer {
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun write(p0: MutableList<datadog.trace.core.DDSpan>?) {
        TODO("Not yet implemented")
    }

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun flush(): Boolean {
        TODO("Not yet implemented")
    }

    override fun incrementDropCounts(p0: Int) {
        TODO("Not yet implemented")
    }
}
