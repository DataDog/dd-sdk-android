/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

/**
 * Holds information about the current network state.
 *
 * @property connectivity the current connectivity
 * @property carrierName information about the network carrier, or null
 * @property carrierId network carrier ID, or null
 * @property upKbps the upload speed in kilobytes per second
 * @property downKbps the download speed in kilobytes per second
 * @property strength the strength of the signal (the unit depends on the type of the signal)
 * @property cellularTechnology the type of cellular technology if known (e.g.: GPRS, LTE, 5G)
 */
data class NetworkInfo(
    val connectivity: Connectivity,
    val carrierName: String?,
    val carrierId: Long?,
    val upKbps: Long?,
    val downKbps: Long?,
    val strength: Long?,
    val cellularTechnology: String?
) {
    /**
     * The type of connectivity currently available.
     */
    enum class Connectivity {
        NETWORK_NOT_CONNECTED,
        NETWORK_ETHERNET,
        NETWORK_WIFI,
        NETWORK_WIMAX,
        NETWORK_BLUETOOTH,
        NETWORK_2G,
        NETWORK_3G,
        NETWORK_4G,
        NETWORK_5G,
        NETWORK_MOBILE_OTHER,
        NETWORK_CELLULAR,
        NETWORK_OTHER
    }
}
