/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.user

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.v2.api.context.UserInfo

internal class DatadogUserInfoProvider(
    internal val dataWriter: DataWriter<UserInfo>
) : MutableUserInfoProvider {

    private var internalUserInfo = UserInfo()
        set(value) {
            field = value
            @Suppress("ThreadSafety") // TODO RUMM-1503 delegate to another thread
            dataWriter.write(field)
        }

    override fun setUserInfo(userInfo: UserInfo) {
        internalUserInfo = userInfo
    }

    override fun addUserProperties(properties: Map<String, Any?>) {
        internalUserInfo = internalUserInfo.copy(
            additionalProperties = internalUserInfo.additionalProperties + properties
        )
    }

    override fun getUserInfo(): UserInfo {
        return internalUserInfo
    }
}
