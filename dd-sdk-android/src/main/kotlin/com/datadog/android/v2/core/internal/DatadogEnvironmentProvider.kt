/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.EnvironmentProvider
import java.io.File

internal class DatadogEnvironmentProvider(
    private val coreFeature: CoreFeature
) : EnvironmentProvider {
    override val trackingConsent: TrackingConsent
        get() = coreFeature.trackingConsentProvider.getConsent()
    override val rootStorageDir: File
        get() = coreFeature.storageDir
}
