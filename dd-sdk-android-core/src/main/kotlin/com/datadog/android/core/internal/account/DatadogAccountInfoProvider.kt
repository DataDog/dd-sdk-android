/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.account

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.AccountInfo
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger

internal class DatadogAccountInfoProvider(
    private val internalLogger: InternalLogger
) : MutableAccountInfoProvider {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

    @Volatile
    private var internalAccountInfo: AccountInfo? = null

    override fun setAccountInfo(
        id: String,
        name: String?,
        extraInfo: Map<String, Any?>
    ) {
        internalAccountInfo = internalAccountInfo?.copy(
            id = id,
            name = name,
            extraInfo = extraInfo.toMap()
        ) ?: AccountInfo(
            id = id,
            name = name,
            extraInfo = extraInfo.toMap()
        )
    }

    override fun addExtraInfo(extraInfo: Map<String, Any?>) {
        internalAccountInfo?.let {
            internalAccountInfo = it.copy(
                extraInfo = it.extraInfo + extraInfo
            )
        } ?: run {
            logger.logAccountNull()
        }
    }

    override fun clearAccountInfo() {
        internalAccountInfo = null
    }

    override fun getAccountInfo(): AccountInfo? {
        return internalAccountInfo
    }

}
