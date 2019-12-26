/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.datadog.android.log.internal.utils.sdkLogger

@TargetApi(Build.VERSION_CODES.N)
internal class CallbackNetworkInfoProvider :
    ConnectivityManager.NetworkCallback(),
    NetworkInfoProvider {

    private var networkInfo: NetworkInfo = NetworkInfo()

    // region NetworkCallback

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities)
        sdkLogger.v("onCapabilitiesChanged $network $networkCapabilities")

        val type = if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            NetworkInfo.Connectivity.NETWORK_WIFI
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            NetworkInfo.Connectivity.NETWORK_CELLULAR
        } else {
            NetworkInfo.Connectivity.NETWORK_OTHER
        }

        networkInfo = NetworkInfo(type)
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        sdkLogger.i("onLost $network")
        networkInfo = NetworkInfo(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
    }

    // endregion

    //region NetworkInfoProvider

    override fun register(context: Context) {
        val connectivityMgr =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityMgr.registerDefaultNetworkCallback(this)
    }

    override fun unregister(context: Context) {
        val connectivityMgr =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityMgr.unregisterNetworkCallback(this)
    }

    override fun getLatestNetworkInfo(): NetworkInfo {
        return networkInfo
    }

    // endregion
}
