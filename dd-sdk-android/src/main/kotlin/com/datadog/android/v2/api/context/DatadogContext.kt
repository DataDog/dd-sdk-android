/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

import com.datadog.android.privacy.TrackingConsent

/**
 * Contains system information, as well as user-specific and feature specific context info.
 * @property time the current time (both device and server)
 * @property applicationInfo static information about the host application (package name, …)
 * @property processInfo information about the current process
 * @property networkInfo information about the current network availability and quality
 * @property userInfo information about the current user
 * @property trackingConsent information about the current tracking consent
 * @property featuresContext agnostic dictionary with information from all features registered to
 * the parent SDK instance
 */
data class DatadogContext(
    val time: TimeInfo,
    val applicationInfo: ApplicationInfo,
    val processInfo: ProcessInfo,
    val networkInfo: NetworkInfo,
    val userInfo: UserInfo,
    val trackingConsent: TrackingConsent,
    val featuresContext: Map<String, Any>
)
