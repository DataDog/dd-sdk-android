package com.datadog.android.log.internal.file

import com.datadog.android.log.internal.LogWriter
import com.datadog.android.log.internal.domain.Log

/**
 * Dummy Log Writer.
 */
internal class DummyLogWriter : LogWriter {
    override fun writeLog(log: Log) {
        // NO-OP
    }
}
