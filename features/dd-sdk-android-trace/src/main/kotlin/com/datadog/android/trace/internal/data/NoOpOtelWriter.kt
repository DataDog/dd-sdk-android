/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.data

import com.datadog.trace.common.writer.Writer

internal class NoOpOtelWriter : Writer {
    override fun close() {
    }

    override fun write(p0: MutableList<com.datadog.trace.core.DDSpan>?) {
    }

    override fun start() {
    }

    override fun flush(): Boolean {
        return true
    }

    override fun incrementDropCounts(p0: Int) {
    }
}
