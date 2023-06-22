/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

/**
 * Holds information about the network carrier.
 *
 * @property technology the technology (e.g. 3G, edge, …), or null
 * @property carrierName the user facing carrier name, or null
 */
data class CarrierInfo(
    val technology: String?,
    val carrierName: String?
)