/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.user

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.model.UserInfo

internal class DatadogUserInfoProvider(
    internal val dataWriter: DataWriter<UserInfo>
) : MutableUserInfoProvider {

    private var internalUserInfo = UserInfo()
        set(value) {
            field = value
            dataWriter.write(field)
        }

    override fun setUserInfo(userInfo: UserInfo) {
        internalUserInfo = userInfo
    }

    override fun addUserProperties(properties: Map<String, Any?>) {
        // keep any existing properties
        val currentProperties = internalUserInfo.additionalProperties

        internalUserInfo = internalUserInfo.copy(
            // preserve current properties. in the event of a conflict, let the new properties prevail
            additionalProperties = currentProperties + properties
        )
    }

    override fun getUserInfo(): UserInfo {
        return internalUserInfo
    }
}
