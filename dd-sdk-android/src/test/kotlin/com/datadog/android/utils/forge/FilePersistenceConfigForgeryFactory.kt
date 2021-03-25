/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FilePersistenceConfigForgeryFactory : ForgeryFactory<FilePersistenceConfig> {
    override fun getForgery(forge: Forge): FilePersistenceConfig {
        return FilePersistenceConfig(
            recentDelayMs = forge.aPositiveLong(),
            maxBatchSize = forge.aPositiveLong(),
            maxItemsPerBatch = forge.aBigInt(),
            oldFileThreshold = forge.aPositiveLong(),
            maxDiskSpace = forge.aPositiveLong()
        )
    }
}
