/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewRumFeature(
    private val coreFeature: CoreFeature
) {

    internal var dataWriter: DataWriter<Any> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    // region SdkFeature

    fun initialize() {
        dataWriter = createDataWriter()
        initialized.set(true)
    }

    fun stop() {
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    private fun createDataWriter(): DataWriter<Any> {
        return RumDataWriter(
            serializer = RumEventSerializer(),
            fileWriter = BatchFileReaderWriter.create(
                internalLogger,
                coreFeature.localDataEncryption
            ),
            internalLogger = internalLogger,
            lastViewEventFile = DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir)

        )
    }

    // endregion

    companion object {
        internal const val WEB_RUM_FEATURE_NAME = "web-rum"
    }
}
