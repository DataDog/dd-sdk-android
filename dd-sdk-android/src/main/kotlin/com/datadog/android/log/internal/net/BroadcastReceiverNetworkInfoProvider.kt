/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo as AndroidNetworkInfo
import android.os.Build
import android.telephony.TelephonyManager
import com.datadog.android.log.internal.utils.sdkLogger

@Suppress("DEPRECATION")
internal class BroadcastReceiverNetworkInfoProvider :
    BroadcastReceiver(), NetworkInfoProvider {

    private var networkInfo: NetworkInfo = NetworkInfo()

    // region BroadcastReceiver

    override fun onReceive(context: Context, intent: Intent?) {
        sdkLogger.d("$TAG: received network update")
        val connectivityMgr =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetworkInfo = connectivityMgr?.activeNetworkInfo

        networkInfo = buildNetworkInfo(context, activeNetworkInfo)
    }

    // endregion

    // region NetworkInfoProvider

    override fun register(context: Context) {
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val firstIntent = context.registerReceiver(this, filter)
        onReceive(context, firstIntent)
    }

    override fun unregister(context: Context) {
        context.unregisterReceiver(this)
    }

    override fun getLatestNetworkInfo(): NetworkInfo {
        return networkInfo
    }

    // endregion

    // region Internal

    private fun buildNetworkInfo(
        context: Context,
        activeNetworkInfo: AndroidNetworkInfo?
    ): NetworkInfo {
        return if (activeNetworkInfo == null || !activeNetworkInfo.isConnected) {
            NetworkInfo(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
        } else if (activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI) {
            NetworkInfo(NetworkInfo.Connectivity.NETWORK_WIFI)
        } else if (activeNetworkInfo.type == ConnectivityManager.TYPE_ETHERNET) {
            NetworkInfo(NetworkInfo.Connectivity.NETWORK_ETHERNET)
        } else if (activeNetworkInfo.type in knownMobileTypes) {
            buildMobileNetworkInfo(context, activeNetworkInfo.subtype)
        } else {
            NetworkInfo(NetworkInfo.Connectivity.NETWORK_OTHER)
        }
    }

    private fun buildMobileNetworkInfo(context: Context, subtype: Int): NetworkInfo {
        val connectivity = when (subtype) {
            in known2GSubtypes -> NetworkInfo.Connectivity.NETWORK_2G
            in known3GSubtypes -> NetworkInfo.Connectivity.NETWORK_3G
            in known4GSubtypes -> NetworkInfo.Connectivity.NETWORK_4G
            in known5GSubtypes -> NetworkInfo.Connectivity.NETWORK_5G
            else -> NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telephonyMgr =
                context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val carrierName = telephonyMgr?.simCarrierIdName ?: UNKNOWN_CARRIER_NAME
            val carrierId = telephonyMgr?.simCarrierId ?: -1
            NetworkInfo(connectivity, carrierName.toString(), carrierId)
        } else {
            NetworkInfo(connectivity)
        }
    }

    // endregion

    companion object {

        private val knownMobileTypes = setOf(
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_MOBILE_DUN,
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            ConnectivityManager.TYPE_MOBILE_MMS,
            ConnectivityManager.TYPE_MOBILE_SUPL
        )

        private val known2GSubtypes = setOf(
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM
        )

        private val known3GSubtypes = setOf(
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA
        )

        private val known4GSubtypes = setOf(
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN,
            19 // @Hide TelephonyManager.NETWORK_TYPE_LTE_CA,
        )

        private val known5GSubtypes = setOf(
            TelephonyManager.NETWORK_TYPE_NR
        )

        private const val UNKNOWN_CARRIER_NAME = "Unknown Carrier Name"

        private const val TAG = "BroadcastReceiver"
    }
}
