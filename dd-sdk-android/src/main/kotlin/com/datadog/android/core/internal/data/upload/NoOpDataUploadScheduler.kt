package com.datadog.android.core.internal.data.upload

internal class NoOpDataUploadScheduler : UploadScheduler {
    override fun startScheduling() {
        // No Op
    }
    override fun stopScheduling() {
        // No Op
    }
}
