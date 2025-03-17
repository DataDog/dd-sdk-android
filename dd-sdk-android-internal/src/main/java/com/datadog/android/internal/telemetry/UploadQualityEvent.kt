/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

/**
 * The different categories of upload quality events.
 * - COUNT: The number of uploads.
 * - BLOCKER: The count of different blockers that prevented an upload.
 * - FAILURE: The count of different failure codes.
 */
enum class UploadQualityCategory {
    COUNT,
    BLOCKER,
    FAILURE
}

/**
 * The different types of blockers that can prevent an upload.
 *
 * @param key The key to be used for the blocker when we send telemetry.
 *
 * - LOW_BATTERY: The device has low battery.
 * - LOW_POWER_MODE: The device is in power save mode.
 * - OFFLINE: The device is offline.
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
    class UploadQualityCountEvent(
        track: String,
        uploadDelay: Int
    ) : UploadQualityEvent(
        track = track,
        uploadDelay = uploadDelay
    )

    class UploadQualityBlockerEvent(
        track: String,
        uploadDelay: Int,
        val blockers: List<String>
    ) : UploadQualityEvent(
        track = track,
        uploadDelay = uploadDelay
    )

    class UploadQualityFailureEvent(
        track: String,
        uploadDelay: Int,
        val failure: String
    ) : UploadQualityEvent(
        track = track,
        uploadDelay = uploadDelay
    )
}
