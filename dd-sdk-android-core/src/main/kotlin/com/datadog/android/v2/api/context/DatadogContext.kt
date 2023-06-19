/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

import com.datadog.android.DatadogSite
import com.datadog.android.privacy.TrackingConsent

/**
 * Contains system information, as well as user-specific and feature specific context info.
 * @property site [Datadog Site](https://docs.datadoghq.com/getting_started/site/) for data uploads.
 * @property clientToken the client token allowing for data uploads to
 * [Datadog Site](https://docs.datadoghq.com/getting_started/site/).
 * @property service the name of the service that data is generated from. Used for
 * [Unified Service Tagging](https://docs.datadoghq.com/getting_started/tagging/unified_service_tagging).
 * @property env the name of the environment that data is generated from. Used for
 * [Unified Service Tagging](https://docs.datadoghq.com/getting_started/tagging/unified_service_tagging).
 * @property version the version of the application that data is generated from. Used for
 * [Unified Service Tagging](https://docs.datadoghq.com/getting_started/tagging/unified_service_tagging).
 * @property variant the name of the application variant (if applies).
 * @property source denotes the mobile application's platform, such as "ios" or "flutter" that
 * data is generated from. See: Datadog [Reserved Attributes](https://docs.datadoghq.com/logs/log_configuration/attributes_naming_convention/#reserved-attributes).
 * @property sdkVersion the version of SDK.
 * @property time the current time (both device and server)
 * @property processInfo information about the current process
 * @property networkInfo information about the current network availability and quality
 * @property deviceInfo information about device
 * @property userInfo information about the current user
 * @property trackingConsent information about the current tracking consent
 * @property featuresContext agnostic dictionary with information from all features registered to
 * the parent SDK instance
 */
data class DatadogContext(
    val site: DatadogSite,
    val clientToken: String,
    val service: String,
    val env: String,
    val version: String,
    val variant: String,
    val source: String,
    val sdkVersion: String,
    val time: TimeInfo,
    val processInfo: ProcessInfo,
    val networkInfo: NetworkInfo,
    val deviceInfo: DeviceInfo,
    val userInfo: UserInfo,
    val trackingConsent: TrackingConsent,
    val featuresContext: Map<String, Map<String, Any?>>
)
