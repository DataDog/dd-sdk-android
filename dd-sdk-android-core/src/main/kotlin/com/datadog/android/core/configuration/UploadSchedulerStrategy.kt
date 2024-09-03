/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.api.feature.Feature

/**
 * Defines the strategy used to schedule the waiting period between batch uploads.
 */
interface UploadSchedulerStrategy {

    /**
     * Should return the delay in milliseconds to wait until the next upload attempt
     * is performed.
     * @param featureName the name of the feature for which a new upload will be scheduled. Known feature names are
     * listed in the [Feature.Companion] object.
     * @param uploadAttempts the number of requests that were attempted during the last upload batch. Will be zero if
     * the device is not ready (e.g.: when offline or with low battery) or no data is ready to be sent.
     * If multiple batches can be uploaded, the attempts will stop at the first failure.
     * @param lastStatusCode the HTTP status code of the last request (if available). A successful upload will have a
     * status code 202 (Accepted). When null, it means that the network request didn't fully complete.
     * @param throwable the exception thrown during the upload process (if any).
     */
    fun getMsDelayUntilNextUpload(
        featureName: String,
        uploadAttempts: Int,
        lastStatusCode: Int?,
        throwable: Throwable?
    ): Long
}
