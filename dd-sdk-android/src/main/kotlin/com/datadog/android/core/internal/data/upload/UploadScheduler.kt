package com.datadog.android.core.internal.data.upload

internal interface UploadScheduler {
    fun startScheduling()
    fun stop()
}
