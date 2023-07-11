/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.UploadFrequency
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FeatureStorageConfigurationForgeryFactory :
    ForgeryFactory<FeatureStorageConfiguration> {
    override fun getForgery(forge: Forge): FeatureStorageConfiguration {
        return FeatureStorageConfiguration(
            maxBatchSize = forge.aPositiveLong(),
            maxItemsPerBatch = forge.aBigInt(),
            maxItemSize = forge.aPositiveLong(),
            oldBatchThreshold = forge.aPositiveLong(),
            uploadFrequency = forge.aNullable { forge.aValueFrom(UploadFrequency::class.java) },
            batchSize = forge.aNullable { forge.aValueFrom(BatchSize::class.java) }
        )
    }
}
