/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import androidx.annotation.WorkerThread
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.NetworkInfo
import java.io.File

/**
 * FOR INTERNAL USAGE ONLY. THIS INTERFACE CONTENT MAY CHANGE WITHOUT NOTICE.
 */
interface InternalSdkCore : SdkCore {

    /**
     * Returns current state of network connection.
     */
    val networkInfo: NetworkInfo

    /**
     * Current tracking consent.
     */
    val trackingConsent: TrackingConsent

    /**
     * Root folder for the hosting SDK instance.
     */
    val rootStorageDir: File

    /**
     * Writes current RUM view event to the dedicated file for the needs of NDK crash reporting.
     *
     * @param data Serialized RUM view event.
     */
    @WorkerThread
    fun writeLastViewEvent(data: ByteArray)
}