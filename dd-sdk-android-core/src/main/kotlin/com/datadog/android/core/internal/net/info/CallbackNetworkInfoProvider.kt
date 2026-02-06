/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net.info

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.internal.system.BuildSdkVersionProvider

@RequiresApi(Build.VERSION_CODES.N)
internal class CallbackNetworkInfoProvider(
    private val buildSdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
    private val internalLogger: InternalLogger
) :
    ConnectivityManager.NetworkCallback(),
    NetworkInfoProvider {

    private var lastNetworkInfo: NetworkInfo = NetworkInfo()

    // region NetworkCallback

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities)
        lastNetworkInfo = NetworkInfo(
            connectivity = getNetworkType(networkCapabilities),
            upKbps = resolveUpBandwidth(networkCapabilities),
            downKbps = resolveDownBandwidth(networkCapabilities),
            strength = resolveStrength(networkCapabilities)
        )
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        lastNetworkInfo = NetworkInfo(connectivity = NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
    }

    // endregion

    //region NetworkInfoProvider

    @Suppress("TooGenericExceptionCaught")
    override fun register(context: Context) {
        val systemService = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        val connMgr = systemService as? ConnectivityManager

        if (connMgr == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { ERROR_REGISTER }
            )
            return
        }

        try {
            connMgr.registerDefaultNetworkCallback(this)
            val activeNetwork = connMgr.activeNetwork
            val activeCaps = connMgr.getNetworkCapabilities(activeNetwork)
            if (activeNetwork != null && activeCaps != null) {
                onCapabilitiesChanged(activeNetwork, activeCaps)
            }
        } catch (e: SecurityException) {
            // RUMM-852 On some devices we get a SecurityException with message
            // "package does not belong to xxxx"
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { ERROR_REGISTER },
                e
            )
            lastNetworkInfo = NetworkInfo(NetworkInfo.Connectivity.NETWORK_OTHER)
        } catch (e: Exception) {
            // RUMM-918 in some cases the device throws a IllegalArgumentException on register
            // "Too many NetworkRequests filed" This happens when registerDefaultNetworkCallback is
            // called too many times without matching unregisterNetworkCallback
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { ERROR_REGISTER },
                e
            )
            lastNetworkInfo = NetworkInfo(NetworkInfo.Connectivity.NETWORK_OTHER)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun unregister(context: Context) {
        val systemService = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        val connMgr = systemService as? ConnectivityManager

        if (connMgr == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { ERROR_UNREGISTER }
            )
            return
        }

        try {
            connMgr.unregisterNetworkCallback(this)
        } catch (e: SecurityException) {
            // RUMM-852 On some devices we get a SecurityException with message
            // "package does not belong to xxxx"
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { ERROR_UNREGISTER },
                e
            )
        } catch (e: RuntimeException) {
            // RUMM-918 in some cases the device throws a IllegalArgumentException on unregister
            // e.g. when the callback was not registered
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { ERROR_UNREGISTER },
                e
            )
        }
    }

    override fun getLatestNetworkInfo(): NetworkInfo {
        return lastNetworkInfo
    }

    // endregion

    // region Internal

    private fun resolveUpBandwidth(networkCapabilities: NetworkCapabilities): Long? {
        return if (networkCapabilities.linkUpstreamBandwidthKbps > 0) {
            networkCapabilities.linkUpstreamBandwidthKbps.toLong()
        } else {
            null
        }
    }

    private fun resolveDownBandwidth(networkCapabilities: NetworkCapabilities): Long? {
        return if (networkCapabilities.linkDownstreamBandwidthKbps > 0) {
            networkCapabilities.linkDownstreamBandwidthKbps.toLong()
        } else {
            null
        }
    }

    private fun resolveStrength(networkCapabilities: NetworkCapabilities): Long? {
        return if (buildSdkVersionProvider.isAtLeastQ &&
            networkCapabilities.signalStrength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        ) {
            networkCapabilities.signalStrength.toLong()
        } else {
            null
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

    companion object {
        internal const val ERROR_REGISTER = "We couldn't register a Network Callback, " +
            "the network information reported will be less accurate."
        internal const val ERROR_UNREGISTER = "We couldn't unregister the Network Callback"
    }
}
