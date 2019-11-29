package com.datadog.android.log.internal.extensions

private const val SDK_LOG_PREFIX = "DD_LOG"
internal fun String.asDataDogTag(): String {
    return "$SDK_LOG_PREFIX+$this"
}
