/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.log.internal.user

internal class NoOpUserInfoProvider : MutableUserInfoProvider {

    // region NoOpUserInfoProvider

    override fun setUserInfo(userInfo: UserInfo) {
        // No Op
    }

    // endregion

    // region UserInfoProvider

    override fun getUserInfo(): UserInfo {
        return UserInfo()
    }

    // endregion
}
