package com.datadog.android.tracing.internal.handlers

import com.datadog.android.log.Logger
import datadog.opentracing.LogsHandler

internal class AndroidLogsHandler(
    val logger: Logger
) : LogsHandler {

    // region Span
    override fun log(timestampMicroseconds: Long, fields: Map<String, *>) {
        // not sure that we should do something with the timestamp here ???
        logFields(fields)
    }

    override fun log(event: String) {
        logger.v(event)
    }

    override fun log(timestampMicroseconds: Long, event: String) {
        // not sure that we should do something with the timestamp here
        logger.v(event)
    }

    override fun log(map: Map<String, *>) {
        logFields(map)
    }

    // endregion

    // region Internal

    private fun logFields(fields: Map<String, *>) {
        fields.forEach {
            logger.addTag(it.key, it.value.toString())
        }
    }

    // endregion
}
