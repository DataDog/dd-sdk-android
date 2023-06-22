/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.v2.api.InternalLogger

internal data class TelemetryCoreConfiguration(
    val trackErrors: Boolean,
    // batchSize.windowDurationMs
    val batchSize: Long,
    // uploadFrequency.baseStepMs
    val batchUploadFrequency: Long,
    val useProxy: Boolean,
    val useLocalEncryption: Boolean
) {
    companion object {
        fun fromEvent(event: Map<*, *>, internalLogger: InternalLogger): TelemetryCoreConfiguration? {
            val trackErrors = event["track_errors"] as? Boolean
            val batchSize = event["batch_size"] as? Long
            val batchUploadFrequency = event["batch_upload_frequency"] as? Long
            val useProxy = event["use_proxy"] as? Boolean
            val useLocalEncryption = event["use_local_encryption"] as? Boolean

            @Suppress("ComplexCondition")
            if (trackErrors == null || batchSize == null || batchUploadFrequency == null ||
                useProxy == null || useLocalEncryption == null
            ) {
                // TODO RUMM-3088 Do an intelligent reporting when message values are missing/have
                //  wrong type, reporting the parameter name and what is exactly wrong
                // this applies to all messages going through the message bus
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    {
                        "One of the mandatory parameters for core configuration telemetry" +
                            " reporting is either missing or have a wrong type."
                    }
                )
                return null
            }

            return TelemetryCoreConfiguration(
                trackErrors = trackErrors,
                batchSize = batchSize,
                batchUploadFrequency = batchUploadFrequency,
                useProxy = useProxy,
                useLocalEncryption = useLocalEncryption
            )
        }
    }
}
