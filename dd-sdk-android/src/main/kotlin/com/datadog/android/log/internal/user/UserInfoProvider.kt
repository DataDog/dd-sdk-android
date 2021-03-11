/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.user

import com.datadog.android.core.model.UserInfo
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface UserInfoProvider {

    fun getUserInfo(): UserInfo
}
