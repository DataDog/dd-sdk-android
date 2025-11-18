/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import com.datadog.android.api.context.UserInfo

internal fun UserInfo.hasUserData(): Boolean {
    return id != null || anonymousId != null || name != null ||
        email != null || additionalProperties.isNotEmpty()
}
