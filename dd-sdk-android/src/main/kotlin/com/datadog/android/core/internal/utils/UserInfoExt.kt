/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.core.model.UserInfo

internal fun UserInfo.hasUserData() : Boolean { return this.id != null || this.name != null ||
        this.email != null || this.additionalProperties.isNotEmpty() }