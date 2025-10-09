/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.datadog.android.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.flags.internal.net.ExposuresRequestFactory
import com.datadog.android.flags.internal.storage.ExposureEventRecordWriter
import com.datadog.android.flags.internal.storage.NoOpRecordWriter
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.log.LogAttributes.RUM_APPLICATION_ID

/**
 * An implementation of [Feature] for getting and reporting
 * feature flags to the RUM dashboard.
 */
internal class FlagsFeature(
    private val sdkCore: FeatureSdkCore,
    @Volatile internal var applicationId: String? = null,
    internal var processor: EventsProcessor = NoOpEventsProcessor(),
    internal var dataWriter: RecordWriter = NoOpRecordWriter()
) :
    StorageBackedFeature,
    FeatureContextUpdateReceiver {

    /**
     * This is the same as the default configuration except
     * that we limit to 50 items per batch, the same as the JS library does.
     */
    override val storageConfiguration =
        FeatureStorageConfiguration.DEFAULT.copy(
            maxItemsPerBatch = MAX_ITEMS_PER_BATCH
        )

    override val requestFactory =
        ExposuresRequestFactory(
            internalLogger = sdkCore.internalLogger
        )

    override val name: String = FLAGS_FEATURE_NAME

    override fun onContextUpdate(
        featureName: String,
        context: Map<String, Any?>
    ) {
        if (featureName == RUM_FEATURE_NAME && applicationId == null) {
            applicationId = context[RUM_APPLICATION_ID]?.toString()
        }
    }

    override fun onInitialize(appContext: Context) {
        sdkCore.setContextUpdateReceiver(this)
        dataWriter = createDataWriter()
        processor = ExposureEventsProcessor(dataWriter)
    }

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
        dataWriter = NoOpRecordWriter()
    }

    private fun createDataWriter(): RecordWriter {
        return ExposureEventRecordWriter(sdkCore)
    }

    internal companion object {
        const val MAX_ITEMS_PER_BATCH = 50
    }
}
