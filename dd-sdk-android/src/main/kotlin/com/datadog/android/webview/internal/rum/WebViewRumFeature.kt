/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.v2.api.EnvironmentProvider
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.core.storage.DataWriter
import com.datadog.android.v2.core.storage.NoOpDataWriter
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewRumFeature(
    override val requestFactory: RequestFactory,
    private val coreFeature: CoreFeature
) : StorageBackedFeature {

    internal var dataWriter: DataWriter<Any> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = WEB_RUM_FEATURE_NAME

    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context,
        environmentProvider: EnvironmentProvider
    ) {
        dataWriter = createDataWriter()
        initialized.set(true)
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    // endregion

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

    companion object {
        internal const val WEB_RUM_FEATURE_NAME = "web-rum"
    }
}
