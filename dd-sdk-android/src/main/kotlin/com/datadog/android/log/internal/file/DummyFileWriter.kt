package com.datadog.android.log.internal.file

import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogWriter

/**
 * Dummy Log File Writer.
 */
internal class DummyFileWriter : LogWriter {
    override fun writeLog(log: Log) {
        // NO-OP
    }
}
