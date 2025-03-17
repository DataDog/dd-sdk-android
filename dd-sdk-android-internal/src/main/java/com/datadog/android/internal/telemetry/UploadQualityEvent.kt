/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

/**
 * The different types of blockers that can prevent an upload.
 *
 * @param key The key to be used for the blocker when we send telemetry.
 */
enum class UploadQualityBlocker(val key: String) {
    LOW_BATTERY("low_battery"),
    LOW_POWER_MODE("lpm"),
    OFFLINE("offline")
}

/**
 * Class to hold the data of an upload quality event.
 * This information is later sent as part of RUM SessionEnded telemetry.
 * @param track The track of the event (e.g. rum, logs, traces).
 * @param uploadDelay The current backoff delay in milliseconds between
 * the last upload attempt and this one.
 */
sealed class UploadQualityEvent(
    val track: String,
    val uploadDelay: Int
) {
    /**
     * Event to report the count of the uploads that are being sent.
     * @param track The track of the event (e.g. rum, logs, traces).
     * @param uploadDelay The current backoff delay in milliseconds between
     */
    class UploadQualityCountEvent(
        track: String,
        uploadDelay: Int
    ) : UploadQualityEvent(
        track = track,
        uploadDelay = uploadDelay
    )

    /**
     * Event to report the blockers that are preventing an upload.
     * @param track The track of the event (e.g. rum, logs, traces).
     * @param uploadDelay The current backoff delay in milliseconds between
     * @param batchCount The number of batches that are being blocked.
     * @param blockers The list of blockers that are preventing the upload
     * The possible blockers are - low power mode, low battery, offline.
     */
    class UploadQualityBlockerEvent(
        track: String,
        uploadDelay: Int,
        val batchCount: Int,
        val blockers: List<String>
    ) : UploadQualityEvent(
        track = track,
        uploadDelay = uploadDelay
    )

    /**
     * Event to report the failure of an upload.
     * @param track The track of the event (e.g. rum, logs, traces).
     * @param uploadDelay The current backoff delay in milliseconds between
     * @param batchCount The number of batches that blocked by the failure.
     * @param failure The status code for the failure.
     */
    class UploadQualityFailureEvent(
        track: String,
        uploadDelay: Int,
        val batchCount: Int,
        val failure: String
    ) : UploadQualityEvent(
        track = track,
        uploadDelay = uploadDelay
    )
}
