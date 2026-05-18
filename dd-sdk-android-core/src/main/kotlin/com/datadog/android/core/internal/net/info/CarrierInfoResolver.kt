/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.core.internal.net.info

import android.telephony.TelephonyManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider

internal class CarrierInfoResolver(
    private val telephonyManager: TelephonyManager,
    private val internalLogger: InternalLogger,
    private val sdkVersionProvider: BuildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT
) {

    val carrierId: Long?
        get() = telephonyManager.resolveCarrierId()

    val carrierName: String?
        get() = telephonyManager.resolveCarrierName()

    private fun TelephonyManager.resolveCarrierName(): String? = try {
        if (sdkVersionProvider.isAtLeastP) {
            simCarrierIdName?.toString()
        } else {
            networkOperatorName?.takeIf {
                it.isNotEmpty()
            }
        }
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { ERROR_CARRIER_NAME },
            e
        )
        null
    }

    private fun TelephonyManager.resolveCarrierId(): Long? = try {
        if (sdkVersionProvider.isAtLeastP) {
            simCarrierId.takeIf { it != TelephonyManager.UNKNOWN_CARRIER_ID }?.toLong()
        } else {
            networkOperator?.takeIf { it.isNotEmpty() }?.toLongOrNull()
        }
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { ERROR_CARRIER_ID },
            e
        )
        null
    }

    internal companion object {
        internal const val ERROR_CARRIER_ID = "Failed to resolve carrier id information from TelephonyManager."
        internal const val ERROR_CARRIER_NAME = "Failed to resolve carrier name information from TelephonyManager."
    }
}
