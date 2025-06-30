/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.user

import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.internal.persistence.DataWriter

internal class DatadogUserInfoProvider(
    internal val dataWriter: DataWriter<UserInfo>
) : MutableUserInfoProvider {

    private var internalUserInfo = UserInfo()
        set(value) {
            field = value
            @Suppress("ThreadSafety") // TODO RUM-3756 delegate to another thread
            dataWriter.write(field)
        }

    override fun setUserInfo(id: String?, name: String?, email: String?, extraInfo: Map<String, Any?>) {
        internalUserInfo = internalUserInfo.copy(
            id = id,
            name = name,
            email = email,
            additionalProperties = extraInfo.toMap()
        )
    }

    override fun setAnonymousId(id: String?) {
        internalUserInfo = internalUserInfo.copy(
            anonymousId = id
        )
    }

    override fun addUserProperties(properties: Map<String, Any?>) {
        internalUserInfo = internalUserInfo.copy(
            additionalProperties = internalUserInfo.additionalProperties + properties
        )
    }

    override fun clearUserInfo() {
        internalUserInfo = UserInfo()
    }

    override fun getUserInfo(): UserInfo {
        return internalUserInfo
    }
}
