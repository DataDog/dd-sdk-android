package com.datadog.android.sdk.utils

internal fun String.isTracesUrl(): Boolean {
    return this.matches(Regex("(.*)/traces/(.*)"))
}

internal fun String.isLogsUrl(): Boolean {
    return this.matches(Regex("(.*)/logs/(.*)"))
}

internal fun String.isRumUrl(): Boolean {
    return this.matches(Regex("(.*)/rum/(.*)"))
}
