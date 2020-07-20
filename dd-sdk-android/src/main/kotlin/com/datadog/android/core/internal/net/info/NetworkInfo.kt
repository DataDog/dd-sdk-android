/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

internal data class NetworkInfo(
    val connectivity: Connectivity = Connectivity.NETWORK_NOT_CONNECTED,
    val carrierName: String? = null,
    val carrierId: Int = -1,
    val upKbps: Int = -1,
    val downKbps: Int = -1,
    val strength: Int = Int.MIN_VALUE,
    val cellularTechnology: String? = null
) {

    fun isConnected(): Boolean {
        return connectivity != Connectivity.NETWORK_NOT_CONNECTED
    }

    internal enum class Connectivity(val serialized: String) {
        NETWORK_NOT_CONNECTED("network_not_connected"),
        NETWORK_ETHERNET("network_ethernet"),
        NETWORK_WIFI("network_wifi"),
        NETWORK_WIMAX("network_wimax"),
        NETWORK_BLUETOOTH("network_bluetooth"),
        NETWORK_2G("network_2g"),
        NETWORK_3G("network_3g"),
        NETWORK_4G("network_4g"),
        NETWORK_5G("network_5g"),
        NETWORK_MOBILE_OTHER("network_mobile_other"),
        NETWORK_CELLULAR("network_cellular"),
        NETWORK_OTHER("network_other")
    }
}
