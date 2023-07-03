/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.context

/**
 * Provides information about device and OS.
 *
 * @property deviceName Device marketing name, e.g. Samsung SM-988GN.
 * @property deviceBrand Device marketing brand, e.g. Samsung.
 * @property deviceModel Device SKU model, e.g. SM-988GN.
 * @property deviceType Device type info.
 * @property deviceBuildId Build Id, ex. "ac45fd".
 * @property osName Operating system name, e.g. Android.
 * @property osMajorVersion Major operating system version, e.g. 8.
 * @property osVersion Full operating system version, e.g. 8.1.1.
 * @property architecture The CPU architecture of the device
 */
data class DeviceInfo(
    val deviceName: String,
    val deviceBrand: String,
    val deviceModel: String,
    val deviceType: DeviceType,
    val deviceBuildId: String,
    val osName: String,
    val osMajorVersion: String,
    val osVersion: String,
    val architecture: String
)
