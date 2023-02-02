/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import com.datadog.android.privacy.TrackingConsent
import java.io.File

/**
 * Low-level interface aimed to support feature-specific functionality.
 */
interface EnvironmentProvider {

    /**
     * Current tracking consent.
     */
    val trackingConsent: TrackingConsent

    /**
     * Root folder for the hosting SDK instance.
     */
    val rootStorageDir: File
}
