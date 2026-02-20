/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.core.internal.persistence.Deserializer
import com.google.gson.JsonParseException

internal class NdkCrashLogDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<String, NdkCrashLog> {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

    override fun deserialize(model: String): NdkCrashLog? {
        return try {
            NdkCrashLog.fromJson(model)
        } catch (e: JsonParseException) {
            logger.logNdkCrashDeserializeError(model = model, throwable = e)
            null
        } catch (e: IllegalStateException) {
            logger.logNdkCrashDeserializeError(model = model, throwable = e)
            null
        }
    }

}
