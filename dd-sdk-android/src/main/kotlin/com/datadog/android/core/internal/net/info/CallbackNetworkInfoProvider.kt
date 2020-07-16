/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.datadog.android.core.internal.utils.sdkLogger

@TargetApi(Build.VERSION_CODES.N)
internal class CallbackNetworkInfoProvider :
    ConnectivityManager.NetworkCallback(),
    NetworkInfoProvider {

    private var networkInfo: NetworkInfo =
        NetworkInfo()

    // region NetworkCallback

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities)
        sdkLogger.v("onCapabilitiesChanged $network $networkCapabilities")

        networkInfo = NetworkInfo(
            connectivity = getNetworkType(networkCapabilities),
            upKbps = networkCapabilities.linkUpstreamBandwidthKbps,
            downKbps = networkCapabilities.linkDownstreamBandwidthKbps,
            strength = getStrength(networkCapabilities)
        )
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        sdkLogger.i("onLost $network")
        networkInfo =
            NetworkInfo(
                NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
            )
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

    // region Internal

    private fun getStrength(networkCapabilities: NetworkCapabilities): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            networkCapabilities.signalStrength
        } else {
            Int.MIN_VALUE
        }
    }

    private fun getNetworkType(networkCapabilities: NetworkCapabilities): NetworkInfo.Connectivity {
        return if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            NetworkInfo.Connectivity.NETWORK_WIFI
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            NetworkInfo.Connectivity.NETWORK_CELLULAR
        } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH
        } else {
            NetworkInfo.Connectivity.NETWORK_OTHER
        }
    }

    // endregion
}
